package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 新增部门或更新部门在组织树中的位置和名称。
 *
 * @property name 部门的显示名称。
 * @property parentId 上级部门 ID，`null` 表示根部门。
 * @property sortOrder 同级部门的排序值，较小值排在前面，默认 `0`。
 */
@Schema(description = "新增部门或更新部门在组织树中的位置和名称。")
data class DepartmentRequest(
    @field:Schema(description = "部门的显示名称。")
    val name: String,
    @field:Schema(description = "上级部门 ID，`null` 表示根部门。")
    val parentId: String? = null,
    @field:Schema(description = "同级部门的排序值，较小值排在前面，默认 `0`。")
    val sortOrder: Int = 0,
)

/**
 * 组织树中的部门节点。
 *
 * @property id 部门 ID。
 * @property name 部门的显示名称。
 * @property sortOrder 同级部门的排序值，较小值排在前面。
 * @property children 直接下级部门，叶节点为空列表。
 */
@Schema(description = "组织树中的部门节点。")
data class DepartmentNode(
    @field:Schema(description = "部门 ID。")
    val id: String,
    @field:Schema(description = "部门的显示名称。")
    val name: String,
    @field:Schema(description = "同级部门的排序值，较小值排在前面。")
    val sortOrder: Int,
    @field:Schema(description = "直接下级部门，叶节点为空列表。")
    val children: List<DepartmentNode>,
)

/**
 * 新增或更新菜单节点的请求。
 *
 * @property name 菜单项的显示名称。
 * @property parentId 上级菜单 ID，`null` 表示顶级菜单。
 * @property path 以 `/` 开头的站内导航路径，`null` 表示未配置跳转。
 * @property permissionCode 菜单可见性所需的权限编码，`null` 表示没有权限点限制。
 * @property sortOrder 同级菜单的排序值，较小值排在前面，默认 `0`。
 * @property enabled 是否启用菜单，默认启用。
 */
@Schema(description = "新增或更新菜单节点的请求。")
data class MenuRequest(
    @field:Schema(description = "菜单项的显示名称。")
    val name: String,
    @field:Schema(description = "上级菜单 ID，`null` 表示顶级菜单。")
    val parentId: String? = null,
    @field:Schema(description = "以 `/` 开头的站内导航路径，`null` 表示未配置跳转。")
    val path: String? = null,
    @field:Schema(description = "菜单可见性所需的权限编码，`null` 表示没有权限点限制。")
    val permissionCode: String? = null,
    @field:Schema(description = "同级菜单的排序值，较小值排在前面，默认 `0`。")
    val sortOrder: Int = 0,
    @field:Schema(description = "是否启用菜单，默认启用。")
    val enabled: Boolean = true,
)

/**
 * 菜单树中的一个导航节点。
 *
 * @property id 菜单 ID。
 * @property name 菜单项的显示名称。
 * @property path 站内导航路径，未配置跳转时为 `null`。
 * @property permissionCode 菜单可见性所需的权限编码，无权限点限制时为 `null`。
 * @property sortOrder 同级菜单的排序值，较小值排在前面。
 * @property enabled 菜单是否启用。
 * @property children 当前树中的直接子菜单，叶节点为空列表。
 */
@Schema(description = "菜单树中的一个导航节点。")
data class MenuNode(
    @field:Schema(description = "菜单 ID。")
    val id: String,
    @field:Schema(description = "菜单项的显示名称。")
    val name: String,
    @field:Schema(description = "站内导航路径，未配置跳转时为 `null`。")
    val path: String?,
    @field:Schema(description = "菜单可见性所需的权限编码，无权限点限制时为 `null`。")
    val permissionCode: String?,
    @field:Schema(description = "同级菜单的排序值，较小值排在前面。")
    val sortOrder: Int,
    @field:Schema(description = "菜单是否启用。")
    val enabled: Boolean,
    @field:Schema(description = "当前树中的直接子菜单，叶节点为空列表。")
    val children: List<MenuNode>,
)
