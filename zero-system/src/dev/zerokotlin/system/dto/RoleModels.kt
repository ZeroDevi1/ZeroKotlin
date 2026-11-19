package dev.zerokotlin.system.dto

import dev.zerokotlin.system.entity.DataScope

/**
 * 新建业务角色的请求。
 *
 * @property code 角色的唯一编码。
 * @property name 角色的显示名称。
 * @property dataScope 此角色授予的权限所适用的数据范围。
 * @property permissionCodes 关联的权限编码集合，最多 256 个，默认不授予权限。
 */
data class CreateRoleRequest(
    val code: String,
    val name: String,
    val dataScope: DataScope,
    val permissionCodes: Set<String> = emptySet(),
)

/**
 * 更新业务角色并替换其全部权限关联的请求。
 *
 * @property name 更新后的角色显示名称。
 * @property dataScope 此角色授予的权限更新后的数据范围。
 * @property enabled 是否启用角色，必填。
 * @property permissionCodes 更新后的完整权限编码集合，最多 256 个，空集合清空关联。
 */
data class UpdateRoleRequest(
    val name: String,
    val dataScope: DataScope,
    val enabled: Boolean?,
    val permissionCodes: Set<String>,
)

/**
 * 角色列表和详情共用的摘要。
 *
 * @property id 角色 ID。
 * @property code 角色的唯一编码。
 * @property name 角色的显示名称。
 * @property dataScope 角色配置的数据范围，以 [DataScope] 的枚举名表示。
 * @property enabled 角色是否启用。
 */
data class RoleSummary(
    val id: String,
    val code: String,
    val name: String,
    val dataScope: String,
    val enabled: Boolean,
)

/**
 * 角色详情，包含角色摘要和关联的权限编码。
 *
 * @property role 角色的基本信息。
 * @property permissionCodes 业务角色返回已关联的编码，内置管理员角色返回全部已启用的权限编码。
 */
data class RoleDetails(val role: RoleSummary, val permissionCodes: List<String>)

/**
 * 新建自定义权限点的请求。
 *
 * @property code 权限点的唯一编码，`system:` 前缀由系统保留。
 * @property name 权限点的显示名称。
 */
data class CreatePermissionRequest(val code: String, val name: String)

/**
 * 修改自定义权限点的名称和状态。
 *
 * @property name 更新后的权限点显示名称。
 * @property enabled 是否启用权限点，必填。
 */
data class UpdatePermissionRequest(val name: String, val enabled: Boolean?)

/**
 * 权限点的查询和写入结果。
 *
 * @property code 权限点的唯一编码，用于角色授权和权限校验。
 * @property name 权限点的显示名称。
 * @property enabled 权限点是否参与有效授权计算。
 */
data class PermissionSummary(val code: String, val name: String, val enabled: Boolean)
