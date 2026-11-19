package dev.zerokotlin.system.dto

/**
 * 新增部门或更新部门在组织树中的位置和名称。
 *
 * @property name 部门的显示名称。
 * @property parentId 上级部门 ID，`null` 表示根部门。
 * @property sortOrder 同级部门的排序值，较小值排在前面，默认 `0`。
 */
data class DepartmentRequest(val name: String, val parentId: String? = null, val sortOrder: Int = 0)

/**
 * 组织树中的部门节点。
 *
 * @property id 部门 ID。
 * @property name 部门的显示名称。
 * @property sortOrder 同级部门的排序值，较小值排在前面。
 * @property children 直接下级部门，叶节点为空列表。
 */
data class DepartmentNode(val id: String, val name: String, val sortOrder: Int, val children: List<DepartmentNode>)

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
data class MenuRequest(
    val name: String,
    val parentId: String? = null,
    val path: String? = null,
    val permissionCode: String? = null,
    val sortOrder: Int = 0,
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
data class MenuNode(
    val id: String,
    val name: String,
    val path: String?,
    val permissionCode: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val children: List<MenuNode>,
)
