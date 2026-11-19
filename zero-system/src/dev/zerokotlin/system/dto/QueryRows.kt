package dev.zerokotlin.system.dto

/**
 * 用户、岗位成员和会话查询共用的用户投影。
 *
 * @property id 用户 ID。
 * @property username 账号的登录名。
 * @property displayName 界面展示的用户名称。
 * @property departmentId 所属部门 ID，未分配时为 `null`。
 * @property enabled 账号状态，`1` 表示启用，`0` 表示停用。
 */
class UserRow(
    var id: String = "",
    var username: String = "",
    var displayName: String = "",
    var departmentId: String? = null,
    var enabled: Int = 1,
)

/**
 * 一条用户与已分配角色的关联查询结果，包含停用的角色。
 *
 * @property userId 获分配角色的用户 ID。
 * @property roleId 被分配的角色 ID。
 * @property code 角色的唯一编码。
 * @property name 角色的显示名称。
 */
class AssignedRoleRow(
    var userId: String = "",
    var roleId: String = "",
    var code: String = "",
    var name: String = "",
)

/**
 * 单个已启用的业务角色授予的一条有效权限，用于在会话认证时合并授权。
 *
 * @property code 权限点的唯一编码。
 * @property dataScope 授予该权限的角色所配置的数据范围枚举名。
 */
class AuthorityRow(
    var code: String = "",
    var dataScope: String = "",
)
