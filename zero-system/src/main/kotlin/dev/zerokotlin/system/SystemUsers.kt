package dev.zerokotlin.system

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper
import dev.zerokotlin.core.PageResult
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

const val USER_READ = "system:user:read"
const val USER_UPDATE = "system:user:update"
const val USER_MANAGE = "system:user:manage"

class CreateUserRequest(
    val username: String,
    val displayName: String,
    val password: String,
    val departmentId: String? = null,
    val roleIds: Set<String> = emptySet(),
)

class UserAccountRequest(
    val departmentId: String? = null,
    val enabled: Boolean?,
    val roleIds: Set<String>,
    val password: String? = null,
)

data class UserProfileRequest(val displayName: String)
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val enabled: Boolean,
)

data class UserDetails(val user: UserSummary, val roleIds: List<String>)
data class AssignedRole(val id: String, val code: String, val name: String)
data class UserListEntry(val user: UserSummary, val roles: List<AssignedRole>)
data class CreatedResource(val id: String)

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
        val passwordHash = passwords.encode(checkedPassword(request.password))
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
        val passwordHash = request.password?.let { passwords.encode(checkedPassword(it)) }
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

@RestController
@RequestMapping("/api/system/users")
class SystemUserController(private val users: SystemUsers) {
    @GetMapping
    @PreAuthorize("hasAuthority('$USER_READ')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) departmentId: String?,
        @RequestParam(required = false) roleId: String?,
    ): PageResult<UserListEntry> = users.list(PageWindow(page, size), departmentId, roleId)

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('$USER_READ')")
    fun detail(@PathVariable id: String): UserDetails = users.detail(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    fun create(@RequestBody request: CreateUserRequest): CreatedResource = users.create(request)

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('$USER_UPDATE')")
    fun updateProfile(@PathVariable id: String, @RequestBody request: UserProfileRequest): UserSummary =
        users.updateProfile(id, request)

    @PutMapping("/{id}/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$USER_MANAGE')")
    fun updateAccount(@PathVariable id: String, @RequestBody request: UserAccountRequest) =
        users.updateAccount(id, request)
}
