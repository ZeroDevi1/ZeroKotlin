package dev.zerokotlin.system.service

import dev.zerokotlin.core.PageResult
import dev.zerokotlin.core.PageWindow
import dev.zerokotlin.system.dto.CreatePermissionRequest
import dev.zerokotlin.system.dto.CreateRoleRequest
import dev.zerokotlin.system.dto.CreatedResource
import dev.zerokotlin.system.dto.PermissionSummary
import dev.zerokotlin.system.dto.RoleDetails
import dev.zerokotlin.system.dto.RoleSummary
import dev.zerokotlin.system.dto.UpdatePermissionRequest
import dev.zerokotlin.system.dto.UpdateRoleRequest
import dev.zerokotlin.system.entity.PermissionRecord
import dev.zerokotlin.system.entity.RoleRecord
import dev.zerokotlin.system.mapper.PermissionMapper
import dev.zerokotlin.system.mapper.RoleMapper
import dev.zerokotlin.system.mapper.SystemRelationMapper
import dev.zerokotlin.system.security.ADMIN_ROLE_ID
import dev.zerokotlin.system.security.DEPARTMENT_WRITE
import dev.zerokotlin.system.security.PERMISSION_WRITE
import dev.zerokotlin.system.security.POSITION_WRITE
import dev.zerokotlin.system.security.ROLE_WRITE
import dev.zerokotlin.system.security.USER_MANAGE
import dev.zerokotlin.system.support.checkedCode
import dev.zerokotlin.system.support.checkedText
import dev.zerokotlin.system.support.conflict
import dev.zerokotlin.system.support.flag
import dev.zerokotlin.system.support.invalid
import dev.zerokotlin.system.support.missing
import dev.zerokotlin.system.support.newId
import dev.zerokotlin.system.support.query
import dev.zerokotlin.system.support.requiredFlag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val administratorPermissions = setOf(USER_MANAGE, ROLE_WRITE, PERMISSION_WRITE, DEPARTMENT_WRITE, POSITION_WRITE)

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
