package dev.zerokotlin.system.dto

import dev.zerokotlin.system.entity.DataScope
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 新建业务角色的请求。
 *
 * @property code 角色的唯一编码。
 * @property name 角色的显示名称。
 * @property dataScope 此角色授予的权限所适用的数据范围。
 * @property permissionCodes 关联的权限编码集合，最多 256 个，默认不授予权限。
 */
@Schema(description = "新建业务角色的请求。")
data class CreateRoleRequest(
    @field:Schema(description = "角色的唯一编码。")
    val code: String,
    @field:Schema(description = "角色的显示名称。")
    val name: String,
    @field:Schema(description = "此角色授予的权限所适用的数据范围。")
    val dataScope: DataScope,
    @field:Schema(description = "关联的权限编码集合，最多 256 个，默认不授予权限。")
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
@Schema(description = "更新业务角色并替换其全部权限关联的请求。")
data class UpdateRoleRequest(
    @field:Schema(description = "更新后的角色显示名称。")
    val name: String,
    @field:Schema(description = "此角色授予的权限更新后的数据范围。")
    val dataScope: DataScope,
    @field:Schema(description = "是否启用角色，必填。", requiredMode = Schema.RequiredMode.REQUIRED)
    val enabled: Boolean?,
    @field:Schema(description = "更新后的完整权限编码集合，最多 256 个，空集合清空关联。")
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
@Schema(description = "角色列表和详情共用的摘要。")
data class RoleSummary(
    @field:Schema(description = "角色 ID。")
    val id: String,
    @field:Schema(description = "角色的唯一编码。")
    val code: String,
    @field:Schema(description = "角色的显示名称。")
    val name: String,
    @field:Schema(
        description = "角色配置的数据范围，以 DataScope 的枚举名表示。",
        allowableValues = ["SELF", "DEPARTMENT", "DEPARTMENT_AND_DESCENDANTS", "ALL"],
    )
    val dataScope: String,
    @field:Schema(description = "角色是否启用。")
    val enabled: Boolean,
)

/**
 * 角色详情，包含角色摘要和关联的权限编码。
 *
 * @property role 角色的基本信息。
 * @property permissionCodes 业务角色返回已关联的编码，内置管理员角色返回全部已启用的权限编码。
 */
@Schema(description = "角色详情，包含角色摘要和关联的权限编码。")
data class RoleDetails(
    @field:Schema(description = "角色的基本信息。")
    val role: RoleSummary,
    @field:Schema(description = "业务角色返回已关联的编码，内置管理员角色返回全部已启用的权限编码。")
    val permissionCodes: List<String>,
)

/**
 * 新建自定义权限点的请求。
 *
 * @property code 权限点的唯一编码，`system:` 前缀由系统保留。
 * @property name 权限点的显示名称。
 */
@Schema(description = "新建自定义权限点的请求。")
data class CreatePermissionRequest(
    @field:Schema(description = "权限点的唯一编码，`system:` 前缀由系统保留。")
    val code: String,
    @field:Schema(description = "权限点的显示名称。")
    val name: String,
)

/**
 * 修改自定义权限点的名称和状态。
 *
 * @property name 更新后的权限点显示名称。
 * @property enabled 是否启用权限点，必填。
 */
@Schema(description = "修改自定义权限点的名称和状态。")
data class UpdatePermissionRequest(
    @field:Schema(description = "更新后的权限点显示名称。")
    val name: String,
    @field:Schema(description = "是否启用权限点，必填。", requiredMode = Schema.RequiredMode.REQUIRED)
    val enabled: Boolean?,
)

/**
 * 权限点的查询和写入结果。
 *
 * @property code 权限点的唯一编码，用于角色授权和权限校验。
 * @property name 权限点的显示名称。
 * @property enabled 权限点是否参与有效授权计算。
 */
@Schema(description = "权限点的查询和写入结果。")
data class PermissionSummary(
    @field:Schema(description = "权限点的唯一编码，用于角色授权和权限校验。")
    val code: String,
    @field:Schema(description = "权限点的显示名称。")
    val name: String,
    @field:Schema(description = "权限点是否参与有效授权计算。")
    val enabled: Boolean,
)
