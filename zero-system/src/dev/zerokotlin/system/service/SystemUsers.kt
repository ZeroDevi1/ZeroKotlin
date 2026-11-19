package dev.zerokotlin.system.service

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.core.security.hashPassword
import dev.zerokotlin.system.dto.AssignedRole
import dev.zerokotlin.system.dto.CreateUserRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.UserAccountRequest
import dev.zerokotlin.system.dto.UserDetails
import dev.zerokotlin.system.dto.UserListEntry
import dev.zerokotlin.system.dto.UserProfileRequest
import dev.zerokotlin.system.dto.UserQuery
import dev.zerokotlin.system.dto.UserRow
import dev.zerokotlin.system.dto.UserSummary
import dev.zerokotlin.system.entity.SessionRecord
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
import dev.zerokotlin.system.support.checkedPassword
import dev.zerokotlin.system.support.checkedText
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

private fun UserRow.summary() = UserSummary(id, username, displayName, departmentId, enabled == 1)

@Service
class SystemUsers(
    private val users: UserMapper,
    private val departments: DepartmentMapper,
    private val roles: RoleMapper,
    private val relations: SystemRelationMapper,
    private val sessions: SessionMapper,
    private val passwords: PasswordEncoder,
    private val writes: SystemWrites,
) {
    @Transactional(readOnly = true)
    fun list(window: PageWindow, departmentId: String?, roleId: String?): PageResult<UserListEntry> {
        val filter = UserQuery(currentSystemActor(), USER_READ, departmentId, roleId)
        val page = users.findUsers(filter, window)
        val byUser = if (page.isEmpty()) emptyMap() else {
            relations.findAssignedRoles(page.map { it.id }).groupBy(
                keySelector = { it.userId },
                valueTransform = { AssignedRole(it.roleId, it.code, it.name) },
            )
        }
        val entries = page.map { UserListEntry(it.summary(), byUser[it.id].orEmpty()) }
        return window.result(entries, users.countUsers(filter))
    }

    @Transactional(readOnly = true)
    fun detail(id: String): UserDetails {
        val user = visibleUser(id, currentSystemActor(), USER_READ)
        return UserDetails(user.summary(), relations.findUserRoleIds(user.id))
    }

    @Transactional
    fun create(request: CreateUserRequest): CreatedResource {
        val username = normalizeUsername(request.username) ?: invalid("用户名格式无效")
        val displayName = checkedText(request.displayName, "displayName")
        val passwordHash = passwords.hashPassword(checkedPassword(request.password))
        val actor = writes.authorize(USER_MANAGE, administratorOnly = true)
        if (users.selectCount(query<UserRecord>().eq("username", username)) != 0L) conflict("用户名已存在")
        validateDepartment(request.departmentId)
        validateRoles(request.roleIds)

        val id = newId()
        users.insert(UserRecord(id, username, displayName, passwordHash, request.departmentId))
        setRoles(id, request.roleIds)
        writes.record(actor, "user.create", id)
        return CreatedResource(id)
    }

    @Transactional
    fun updateProfile(id: String, request: UserProfileRequest): UserSummary {
        val displayName = checkedText(request.displayName, "displayName")
        val actor = writes.authorize(USER_UPDATE)
        val user = visibleUser(id, actor, USER_UPDATE)
        users.update(null, UpdateWrapper<UserRecord>().eq("id", user.id).set("display_name", displayName))
        user.displayName = displayName
        writes.record(actor, "user.profile.update", user.id)
        return user.summary()
    }

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
            sessions.delete(query<SessionRecord>().eq("user_id", user.id))
        }
        writes.record(actor, "user.account.update", user.id)
    }

    private fun visibleUser(id: String, actor: SystemActor, authority: String): UserRow =
        users.findVisible(UserQuery(actor, authority), id) ?: missing("用户")

    private fun validateDepartment(id: String?) {
        if (id != null && departments.selectById(id)?.id != id) invalid("部门不存在")
    }

    private fun validateRoles(ids: Set<String>) {
        if (ADMIN_ROLE_ID in ids) invalid("内置管理员角色只能在首次初始化时授予")
        if (ids.size > 256) invalid("单个账号最多关联 256 个业务角色")
        if (ids.isNotEmpty() && roles.selectByIds(ids).map { it.id }.toSet() != ids) invalid("角色不存在")
    }

    private fun setRoles(userId: String, ids: Set<String>) {
        relations.deleteUserRoles(userId)
        ids.forEach { relations.insertUserRole(userId, it) }
    }
}
