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

/** 只授予内置管理员、禁止分配给业务角色的权限编码集合。 */
private val administratorPermissions = setOf(USER_MANAGE, ROLE_WRITE, PERMISSION_WRITE, DEPARTMENT_WRITE, POSITION_WRITE)

/**
 * 将持久化角色记录转换为业务摘要。
 *
 * @receiver 待转换的角色记录。
 * @return 角色资料、数据范围编码及布尔启用状态。
 */
private fun RoleRecord.summary() = RoleSummary(id, code, name, dataScope, enabled == 1)

/**
 * 将权限点记录转换为业务摘要。
 *
 * @receiver 待转换的权限点记录。
 * @return 权限点编码、名称及布尔启用状态。
 */
private fun PermissionRecord.summary() = PermissionSummary(code, name, enabled == 1)

/**
 * 查询角色并维护业务角色与权限的关联。业务角色写入要求系统管理员身份和角色写入权限。
 *
 * @property roles 读写角色资料及数据范围。
 * @property permissions 查询权限目录并检查分配的权限点是否存在。
 * @property relations 查询和替换角色的权限关联。
 * @property writes 串行化写入，在同一事务中校验写入者并记录审计。
 */
@Service
class SystemRoles(
    private val roles: RoleMapper,
    private val permissions: PermissionMapper,
    private val relations: SystemRelationMapper,
    private val writes: SystemWrites,
) {
    /**
     * 在只读事务中按启用状态筛选角色。
     *
     * @param window 页码和每页条数。
     * @param enabled 目标启用状态，为 null 时查询所有状态。
     * @return 按编码和 ID 升序排列的角色摘要页。
     */
    @Transactional(readOnly = true)
    fun list(window: PageWindow, enabled: Boolean?): PageResult<RoleSummary> {
        val flag = enabled?.flag()
        return window.result(roles.findRoles(flag, window).map(RoleRecord::summary), roles.countRoles(flag))
    }

    /**
     * 在只读事务中查询角色的资料与权限。
     *
     * @param id 角色 ID，不存在时返回 404。
     * @return 角色摘要和按编码升序排列的权限列表。内置管理员角色的列表来自所有已启用权限点。
     */
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

    /**
     * 新建业务角色并保存初始权限关联。角色编码重复时返回 409。
     *
     * @param request 唯一编码、名称、数据范围及初始权限编码集合。
     * @return 新角色 ID。
     */
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

    /**
     * 更新业务角色资料，并在同一事务内替换权限关联。
     *
     * @param id 待修改的业务角色 ID。不存在时返回 404，指定内置管理员角色时返回 400。
     * @param request 新名称、数据范围、必填的启用状态和完整权限编码集合。
     */
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

    /**
     * 校验待分配的权限格式、存在性及分配限制，失败时抛出 HTTP 400 异常。
     *
     * @param codes 权限编码集合，最多 256 个，禁止包含内置管理员专用权限。
     */
    private fun validatePermissions(codes: Set<String>) {
        if (codes.size > 256) invalid("单个角色最多关联 256 个权限点")
        if (codes.any { it in administratorPermissions }) invalid("所选权限由内置管理员专用")
        codes.forEach { checkedCode(it) }
        if (codes.isNotEmpty() && permissions.selectByIds(codes).map { it.code }.toSet() != codes) {
            invalid("权限点不存在")
        }
    }

    /**
     * 在调用方事务中清空原有关联，再写入完整的权限集合。
     *
     * @param roleId 待替换权限的角色 ID。
     * @param codes 已校验的权限编码集合，为空时清空关联。
     */
    private fun replacePermissions(roleId: String, codes: Set<String>) {
        relations.deleteRolePermissions(roleId)
        codes.forEach { relations.insertRolePermission(roleId, it) }
    }
}

/**
 * 查询权限目录并维护自定义权限点。权限点写入要求系统管理员身份和权限点写入权限。
 *
 * @property permissions 按编码读写权限点及启用状态。
 * @property writes 串行化权限点写入，重新授权并记录操作审计。
 */
@Service
class SystemPermissions(
    private val permissions: PermissionMapper,
    private val writes: SystemWrites,
) {
    /**
     * 查询权限点目录。
     *
     * @return 按编码升序排列的全部权限点摘要。
     */
    fun list(): List<PermissionSummary> =
        permissions.selectList(query<PermissionRecord>().orderByAsc("code")).map(PermissionRecord::summary)

    /**
     * 校验自定义编码后登记权限点，并在同一事务写入审计。编码重复时返回 409。
     *
     * @param request 自定义权限编码和显示名称。
     * @return 新权限点摘要。
     */
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

    /**
     * 更新自定义权限点的名称和启用状态，并在同一事务写入审计。
     *
     * @param code 待修改的自定义权限编码，不存在时返回 404。
     * @param request 新名称和必填的启用状态。
     * @return 更新后的权限点摘要。
     */
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

    /**
     * 校验编码格式及保留前缀。使用 system: 前缀或格式无效时抛出 HTTP 400 异常。
     *
     * @param value 请求提交的权限编码。
     * @return 通过校验的自定义权限编码。
     */
    private fun editableCode(value: String): String =
        checkedCode(value).also { if (it.startsWith("system:")) invalid("system: 权限由系统维护") }
}
