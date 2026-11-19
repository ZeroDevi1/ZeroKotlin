package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.core.security.PlainTextInput
import dev.zerokotlin.core.security.hashPassword
import dev.zerokotlin.system.dto.AssignedRole
import dev.zerokotlin.system.dto.CreateUserRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.OwnProfile
import dev.zerokotlin.system.dto.OwnProfileRequest
import dev.zerokotlin.system.dto.ResetPasswordRequest
import dev.zerokotlin.system.dto.UserAccountRequest
import dev.zerokotlin.system.dto.UserDetails
import dev.zerokotlin.system.dto.UserListEntry
import dev.zerokotlin.system.dto.UserProfileRequest
import dev.zerokotlin.system.dto.UserQuery
import dev.zerokotlin.system.dto.UserRow
import dev.zerokotlin.system.dto.UserSummary
import dev.zerokotlin.system.entity.UserRecord
import dev.zerokotlin.system.mapper.DepartmentMapper
import dev.zerokotlin.system.mapper.RoleMapper
import dev.zerokotlin.system.mapper.SessionMapper
import dev.zerokotlin.system.mapper.SystemRelationMapper
import dev.zerokotlin.system.mapper.UserMapper
import dev.zerokotlin.system.security.ADMIN_ROLE_ID
import dev.zerokotlin.system.security.SystemActor
import dev.zerokotlin.system.security.USER_MANAGE
import dev.zerokotlin.system.security.USER_READ
import dev.zerokotlin.system.security.USER_UPDATE
import dev.zerokotlin.system.security.currentSystemActor
import dev.zerokotlin.system.support.EXCEL_EXPORT_ROWS
import dev.zerokotlin.system.support.ExcelImportRow
import dev.zerokotlin.system.support.atExcelRow
import dev.zerokotlin.system.support.checkedDeleteIds
import dev.zerokotlin.system.support.checkedPassword
import dev.zerokotlin.system.support.checkedPlainText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.normalizeUsername
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.requiredFlag
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 将查询到的用户行转换为对外摘要。
 *
 * @receiver 从用户查询中读取的一行资料。
 * @return 含身份资料、直属部门及布尔启用状态的用户摘要。
 */
private fun UserRow.summary() = UserSummary(id, username, displayName, departmentId, enabled == 1)

/** 仅输出本人可见的基本资料。 */
private fun UserRecord.ownProfile() = OwnProfile(username, displayName, email, mobile)

/** 简单联系邮箱格式，允许常见邮箱字符及带点的域名。 */
private val emailPattern = Regex("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")

/** 允许纯数字手机号和带国际前缀的号码。 */
private val mobilePattern = Regex("\\+?[0-9]{6,15}")

/**
 * 按权限对应的数据范围读取用户、更新资料，并维护账号及角色分配。
 *
 * @property users 读写用户记录，按数据范围查找目标账号。
 * @property departments 核验账号所属部门是否存在。
 * @property roles 核验待分配的业务角色是否存在。
 * @property relations 查询和整体替换用户的角色关联。
 * @property sessions 在账号停用或设置新密码后删除会话。
 * @property passwords 为新账号或新密码生成密码哈希。
 * @property writes 串行化写入，重新授权并在写事务中记录操作审计。
 * @property plainText 可启用的用户显示名清理器。
 */
@Service
class SystemUsers(
    private val users: UserMapper,
    private val departments: DepartmentMapper,
    private val roles: RoleMapper,
    private val relations: SystemRelationMapper,
    private val sessions: SessionMapper,
    private val passwords: PasswordEncoder,
    private val writes: SystemWrites,
    private val plainText: PlainTextInput,
) {
    /**
     * 在只读事务中按用户读取权限的数据范围查询账号，并批量补齐已分配的角色。
     *
     * @param window 页码和每页条数。
     * @param departmentId 直属部门 ID，null 表示不限部门。
     * @param roleId 已分配的角色 ID，null 表示不限角色。
     * @return 按用户名和 ID 升序排列的用户页，每项包含用户摘要和已分配的角色。
     */
    @Transactional(readOnly = true)
    fun list(window: PageWindow, departmentId: String?, roleId: String?): PageResult<UserListEntry> {
        val filter = UserQuery(currentSystemActor(), USER_READ, departmentId, roleId)
        val entries = listEntries(users.findUsers(filter, window.size, window.offset))
        return window.result(entries, users.countUsers(filter))
    }

    /** 读取授权范围内符合筛选条件的用户和角色，超出单次导出上限时返回 400。 */
    @Transactional(readOnly = true)
    fun forExcel(departmentId: String?, roleId: String?): List<UserListEntry> {
        val filter = UserQuery(currentSystemActor(), USER_READ, departmentId, roleId)
        val rows = users.findUsers(filter, EXCEL_EXPORT_ROWS + 1, 0)
        if (rows.size > EXCEL_EXPORT_ROWS) invalid("导出最多包含 $EXCEL_EXPORT_ROWS 个用户，请缩小筛选范围")
        return rows.chunked(100).flatMap(::listEntries)
    }

    /** 补齐用户行关联的全部角色，保持用户行及其角色的原有顺序。 */
    private fun listEntries(rows: List<UserRow>): List<UserListEntry> {
        if (rows.isEmpty()) return emptyList()
        val byUser = relations.findAssignedRoles(rows.map { it.id }).groupBy(
            keySelector = { it.userId },
            valueTransform = { AssignedRole(it.roleId, it.code, it.name) },
        )
        return rows.map { UserListEntry(it.summary(), byUser[it.id].orEmpty()) }
    }

    /**
     * 在只读事务中读取用户摘要及角色分配，受用户读取权限的数据范围约束。
     *
     * @param id 要查询的用户 ID，目标不存在或超出可见范围时返回 404。
     * @return 用户摘要和按 ID 升序排列的角色 ID 列表。
     */
    @Transactional(readOnly = true)
    fun detail(id: String): UserDetails {
        val user = visibleUser(id, currentSystemActor(), USER_READ)
        return UserDetails(user.summary(), relations.findUserRoleIds(user.id))
    }

    /** 读取当前已认证账号的个人资料。 */
    fun ownProfile(): OwnProfile = (users.selectById(currentSystemActor().user.id) ?: missing("用户")).ownProfile()

    /**
     * 在写入锁下重新认证并保存本人资料。可选联系方式为空时清除。
     *
     * @param request 完整的显示名称和可选联系方式。
     * @return 更新后的个人资料。
     */
    @Transactional
    fun updateOwnProfile(request: OwnProfileRequest): OwnProfile {
        val displayName = plainText.checkedPlainText(request.displayName, "displayName")
        val email = request.email?.trim()?.takeIf { it.isNotEmpty() }
        val mobile = request.mobile?.trim()?.takeIf { it.isNotEmpty() }
        if (email != null && (email.length > 254 || email.any { Character.isISOControl(it) } || !emailPattern.matches(email))) {
            invalid("邮箱格式无效或超过 254 字符")
        }
        if (mobile != null && !mobilePattern.matches(mobile)) invalid("手机号须为 6 至 15 位数字，可带国际前缀 +")
        val actor = writes.lockAndAuthenticate()
        val user = actor.user
        users.update(
            null,
            UpdateWrapper<UserRecord>().eq("id", user.id)
                .set("display_name", displayName).set("email", email).set("mobile", mobile),
        )
        writes.record(actor, "auth.profile.update", user.id)
        return OwnProfile(user.username, displayName, email, mobile)
    }

    /**
     * 校验账号管理权限和系统管理员身份后创建账号，分配业务角色并写入审计。用户名重复时返回 409。
     *
     * @param request 用户名、显示名称、初始密码、可选部门和待分配的业务角色。
     * @return 新用户 ID。
     */
    @Transactional
    fun create(request: CreateUserRequest): CreatedResource {
        val account = prepareAccount(request)
        val actor = writes.authorize(USER_MANAGE, administratorOnly = true)
        return insertAccount(account, request.roleIds, actor)
    }

    /**
     * 按单个账号创建的规则导入整份工作簿。先生成独立密码哈希，再取得写锁并重新授权。
     * 任意一行失败时回滚账号、角色关联及审计。
     */
    @Transactional
    fun importExcel(rows: List<ExcelImportRow<CreateUserRequest>>) {
        val current = currentSystemActor()
        current.requireScope(USER_MANAGE)
        current.requireAdministrator()
        val accounts = rows.map { row -> row to atExcelRow(row.number) { prepareAccount(row.request) } }
        val actor = writes.authorize(USER_MANAGE, administratorOnly = true)
        for ((row, account) in accounts) {
            atExcelRow(row.number) { insertAccount(account, row.request.roleIds, actor) }
        }
    }

    /** 校验账号文本并为初始密码生成哈希，准备插入记录。 */
    private fun prepareAccount(request: CreateUserRequest): UserRecord {
        val username = normalizeUsername(request.username) ?: invalid("用户名格式无效")
        val displayName = plainText.checkedPlainText(request.displayName, "displayName")
        val passwordHash = passwords.hashPassword(checkedPassword(request.password))
        return UserRecord(newId(), username, displayName, passwordHash, request.departmentId)
    }

    /** 在已经授权的写事务内检查账号及引用，创建角色关联并记录账号创建审计。 */
    private fun insertAccount(account: UserRecord, roleIds: Set<String>, actor: SystemActor): CreatedResource {
        if (users.selectCount(query<UserRecord>().eq("username", account.username)) != 0L) conflict("用户名已存在")
        validateDepartment(account.departmentId)
        validateRoles(roleIds)
        users.insert(account)
        setRoles(account.id, roleIds)
        writes.record(actor, "user.create", account.id)
        return CreatedResource(account.id)
    }

    /**
     * 按资料修改权限的数据范围查找目标，更新其显示名称并写入审计。
     *
     * @param id 待修改的用户 ID，目标不存在或不可见时返回 404。
     * @param request 新的显示名称。
     * @return 更新后的用户摘要。
     */
    @Transactional
    fun updateProfile(id: String, request: UserProfileRequest): UserSummary {
        val displayName = plainText.checkedPlainText(request.displayName, "displayName")
        val actor = writes.authorize(USER_UPDATE)
        val user = visibleUser(id, actor, USER_UPDATE)
        users.update(null, UpdateWrapper<UserRecord>().eq("id", user.id).set("display_name", displayName))
        user.displayName = displayName
        writes.record(actor, "user.profile.update", user.id)
        return user.summary()
    }

    /**
     * 校验账号管理权限和系统管理员身份后更新账号并替换角色。停用账号或提交新密码时，在同一事务撤销该账号的全部会话。
     * 内置管理员必须保持启用并保有内置角色。内置角色只在首次初始化时授予。
     *
     * @param id 待修改的用户 ID，不存在或不可见时返回 404。
     * @param request 新部门、必填的启用状态、完整角色集合及可选的新密码。部门为 null 表示无所属部门，
     * 密码为 null 表示沿用当前密码。
     */
    @Transactional
    fun updateAccount(id: String, request: UserAccountRequest) {
        val enabled = request.enabled.requiredFlag()
        val passwordHash = request.password?.let { passwords.hashPassword(checkedPassword(it)) }
        val actor = writes.authorize(USER_MANAGE, administratorOnly = true)
        visibleUser(id, actor, USER_MANAGE)
        val user = users.selectById(id) ?: missing("用户")
        val administrator = ADMIN_ROLE_ID in relations.findUserRoleIds(user.id)
        if (administrator && (enabled != 1 || ADMIN_ROLE_ID !in request.roleIds)) {
            invalid("内置管理员必须保持启用并保留系统管理员角色")
        }
        if (!administrator && ADMIN_ROLE_ID in request.roleIds) invalid("内置管理员角色只能在首次初始化时授予")
        validateDepartment(request.departmentId)
        validateRoles(request.roleIds - ADMIN_ROLE_ID)

        user.departmentId = request.departmentId
        user.enabled = enabled
        if (passwordHash != null) user.passwordHash = passwordHash
        users.updateById(user)
        setRoles(user.id, request.roleIds)
        if (enabled == 0 || passwordHash != null) {
            sessions.revokeForUser(user.id)
        }
        writes.record(actor, "user.account.update", user.id)
    }

    /**
     * 由内置管理员重置目标账号的密码，并在同一事务撤销该账号的全部会话。
     *
     * @param id 待重置的用户 ID。
     * @param request 新密码。
     */
    @Transactional
    fun resetPassword(id: String, request: ResetPasswordRequest) {
        val hash = passwords.hashPassword(checkedPassword(request.newPassword))
        val actor = writes.authorize(USER_MANAGE, administratorOnly = true)
        val user = visibleUser(id, actor, USER_MANAGE)
        users.updatePassword(user.id, hash)
        sessions.revokeForUser(user.id)
        writes.record(actor, "user.password.reset", user.id)
    }

    /**
     * 原子删除业务账号、会话和账号所属关联，保留历史审计。内置管理员账号受保护。
     *
     * @param ids 要删除的用户 ID。
     */
    @Transactional
    fun delete(ids: List<String?>) {
        val actor = writes.authorize(USER_MANAGE, administratorOnly = true)
        val targets = checkedDeleteIds(ids)
        for (id in targets) {
            val user = visibleUser(id, actor, USER_MANAGE)
            if (user.id != id) missing("用户")
            if (ADMIN_ROLE_ID in relations.findUserRoleIds(id)) invalid("内置管理员账号由系统维护")
        }
        for (id in targets) {
            sessions.revokeForUser(id)
            relations.deleteUserRoles(id)
            relations.deleteUserPositions(id)
            relations.deleteUserAnnouncementReads(id)
            if (users.deleteById(id) != 1) missing("用户")
            writes.record(actor, "user.delete", id)
        }
    }

    /**
     * 按操作对应的数据范围查找用户，目标不存在或不在范围内时抛出 HTTP 404 异常。
     *
     * @param id 目标用户 ID。
     * @param actor 已认证的操作主体。
     * @param authority 用于确定数据范围的权限编码。
     * @return 数据范围内匹配的用户行。
     */
    private fun visibleUser(id: String, actor: SystemActor, authority: String): UserRow =
        users.findVisible(UserQuery(actor, authority), id) ?: missing("用户")

    /**
     * 校验非空的部门 ID。部门不存在时抛出 HTTP 400 异常。
     *
     * @param id 用户的直属部门 ID，null 表示未分配部门。
     */
    private fun validateDepartment(id: String?) {
        if (id != null && departments.selectById(id)?.id != id) invalid("部门不存在")
    }

    /**
     * 校验业务角色数量、存在性及分配限制，失败时抛出 HTTP 400 异常。
     *
     * @param ids 待分配的业务角色 ID 集合，最多 256 个。
     */
    private fun validateRoles(ids: Set<String>) {
        if (ADMIN_ROLE_ID in ids) invalid("内置管理员角色只能在首次初始化时授予")
        if (ids.size > 256) invalid("单个账号最多关联 256 个业务角色")
        if (ids.isNotEmpty() && roles.selectByIds(ids).map { it.id }.toSet() != ids) invalid("角色不存在")
    }

    /**
     * 在调用方事务中删除旧关联并写入完整的角色集合。
     *
     * @param userId 待替换角色的用户 ID。
     * @param ids 经调用方校验的角色 ID 集合，为空时清空关联。
     */
    private fun setRoles(userId: String, ids: Set<String>) {
        relations.deleteUserRoles(userId)
        ids.forEach { relations.insertUserRole(userId, it) }
    }
}
