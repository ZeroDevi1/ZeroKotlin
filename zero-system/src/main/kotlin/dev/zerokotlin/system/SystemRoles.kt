package dev.zerokotlin.system

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

const val ROLE_WRITE = "system:role:write"
const val PERMISSION_WRITE = "system:permission:write"
const val DEPARTMENT_WRITE = "system:department:write"

private val administratorPermissions = setOf(USER_MANAGE, ROLE_WRITE, PERMISSION_WRITE, DEPARTMENT_WRITE)

data class CreateRoleRequest(
    val code: String,
    val name: String,
    val dataScope: DataScope,
    val permissionCodes: Set<String> = emptySet(),
)

data class UpdateRoleRequest(
    val name: String,
    val dataScope: DataScope,
    val enabled: Boolean?,
    val permissionCodes: Set<String>,
)

data class RoleSummary(
    val id: String,
    val code: String,
    val name: String,
    val dataScope: String,
    val enabled: Boolean,
)

data class RoleDetails(val role: RoleSummary, val permissionCodes: List<String>)
data class CreatePermissionRequest(val code: String, val name: String)
data class UpdatePermissionRequest(val name: String, val enabled: Boolean?)
data class PermissionSummary(val code: String, val name: String, val enabled: Boolean)

private fun RoleRecord.summary() = RoleSummary(id, code, name, dataScope, enabled == 1)
private fun PermissionRecord.summary() = PermissionSummary(code, name, enabled == 1)

@Service
class SystemRoles(
    private val roles: RoleMapper,
    private val permissions: PermissionMapper,
    private val relations: SystemRelationMapper,
    private val writes: SystemWrites,
) {
    @Transactional(readOnly = true)
    fun list(window: PageWindow, enabled: Boolean?): PageResult<RoleSummary> {
        val flag = enabled?.flag()
        return window.result(roles.findRoles(flag, window).map(RoleRecord::summary), roles.countRoles(flag))
    }

    @Transactional(readOnly = true)
    fun detail(id: String): RoleDetails {
        val role = roles.selectById(id) ?: missing("角色")
        val codes = if (role.id == ADMIN_ROLE_ID) {
            permissions.selectList(query<PermissionRecord>().eq("enabled", 1).orderByAsc("code")).map { it.code }
        } else {
            relations.findRolePermissionCodes(role.id)
        }
        return RoleDetails(role.summary(), codes)
    }

    @Transactional
    fun create(request: CreateRoleRequest): CreatedResource {
        val actor = writes.authorize(ROLE_WRITE, administratorOnly = true)
        val code = checkedCode(request.code)
        val name = checkedText(request.name, "name")
        if (roles.selectCount(query<RoleRecord>().eq("code", code)) != 0L) conflict("角色编码已存在")
        validatePermissions(request.permissionCodes)

        val id = newId()
        roles.insert(RoleRecord(id, code, name, request.dataScope.name))
        replacePermissions(id, request.permissionCodes)
        writes.record(actor, "role.create", id)
        return CreatedResource(id)
    }

    @Transactional
    fun update(id: String, request: UpdateRoleRequest) {
        val actor = writes.authorize(ROLE_WRITE, administratorOnly = true)
        val role = roles.selectById(id) ?: missing("角色")
        if (role.id == ADMIN_ROLE_ID) invalid("内置管理员角色由系统维护")
        validatePermissions(request.permissionCodes)

        role.name = checkedText(request.name, "name")
        role.dataScope = request.dataScope.name
        role.enabled = request.enabled.requiredFlag()
        roles.updateById(role)
        replacePermissions(role.id, request.permissionCodes)
        writes.record(actor, "role.update", role.id)
    }

    private fun validatePermissions(codes: Set<String>) {
        if (codes.size > 256) invalid("单个角色最多关联 256 个权限点")
        if (codes.any { it in administratorPermissions }) invalid("所选权限由内置管理员专用")
        codes.forEach { checkedCode(it) }
        if (codes.isNotEmpty() && permissions.selectByIds(codes).map { it.code }.toSet() != codes) {
            invalid("权限点不存在")
        }
    }

    private fun replacePermissions(roleId: String, codes: Set<String>) {
        relations.deleteRolePermissions(roleId)
        codes.forEach { relations.insertRolePermission(roleId, it) }
    }
}

@Service
class SystemPermissions(
    private val permissions: PermissionMapper,
    private val writes: SystemWrites,
) {
    fun list(): List<PermissionSummary> =
        permissions.selectList(query<PermissionRecord>().orderByAsc("code")).map(PermissionRecord::summary)

    @Transactional
    fun create(request: CreatePermissionRequest): PermissionSummary {
        val actor = writes.authorize(PERMISSION_WRITE, administratorOnly = true)
        val code = editableCode(request.code)
        if (permissions.selectById(code) != null) conflict("权限编码已存在")

        val permission = PermissionRecord(code, checkedText(request.name, "name"))
        permissions.insert(permission)
        writes.record(actor, "permission.create", code)
        return permission.summary()
    }

    @Transactional
    fun update(code: String, request: UpdatePermissionRequest): PermissionSummary {
        val actor = writes.authorize(PERMISSION_WRITE, administratorOnly = true)
        val permission = permissions.selectById(editableCode(code)) ?: missing("权限点")

        permission.name = checkedText(request.name, "name")
        permission.enabled = request.enabled.requiredFlag()
        permissions.updateById(permission)
        writes.record(actor, "permission.update", code)
        return permission.summary()
    }

    private fun editableCode(value: String): String =
        checkedCode(value).also { if (it.startsWith("system:")) invalid("system: 权限由系统维护") }
}

@RestController
@RequestMapping("/api/system/roles")
class SystemRoleController(private val roles: SystemRoles) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:read')")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) enabled: Boolean?,
    ): PageResult<RoleSummary> = roles.list(PageWindow(page, size), enabled)

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:read')")
    fun detail(@PathVariable id: String): RoleDetails = roles.detail(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$ROLE_WRITE')")
    fun create(@RequestBody request: CreateRoleRequest): CreatedResource = roles.create(request)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('$ROLE_WRITE')")
    fun update(@PathVariable id: String, @RequestBody request: UpdateRoleRequest) = roles.update(id, request)
}

@RestController
@RequestMapping("/api/system/permissions")
class SystemPermissionController(private val permissions: SystemPermissions) {
    @GetMapping
    @PreAuthorize("hasAuthority('system:permission:read')")
    fun list(): List<PermissionSummary> = permissions.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('$PERMISSION_WRITE')")
    fun create(@RequestBody request: CreatePermissionRequest): PermissionSummary = permissions.create(request)

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('$PERMISSION_WRITE')")
    fun update(@PathVariable code: String, @RequestBody request: UpdatePermissionRequest): PermissionSummary =
        permissions.update(code, request)
}
