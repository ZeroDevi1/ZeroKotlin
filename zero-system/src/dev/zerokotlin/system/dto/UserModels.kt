package dev.zerokotlin.system.dto

/**
 * 新建用户账号的请求。
 *
 * @property username 登录名，校验格式并统一转为小写后须唯一。
 * @property displayName 新用户的显示名称。
 * @property password 初始密码，至少 12 个 Unicode 码点，最多 72 个 UTF-8 字节。
 * @property departmentId 所属部门 ID，`null` 表示暂不分配部门。
 * @property roleIds 初始业务角色 ID 集合，最多 256 个，默认为空集合。
 */
class CreateUserRequest(
    val username: String,
    val displayName: String,
    val password: String,
    val departmentId: String? = null,
    val roleIds: Set<String> = emptySet(),
)

/**
 * 更新用户的部门、账号状态、角色及可选密码。
 *
 * @property departmentId 更新后的部门 ID，`null` 表示清除部门归属。
 * @property enabled 是否启用账号，必填，停用会撤销该账号的所有会话。
 * @property roleIds 更新后的完整角色 ID 集合。内置管理员必须保留系统管理员角色。
 * @property password 新密码，`null` 表示保留现有密码，重设后会撤销该账号的所有会话。
 */
class UserAccountRequest(
    val departmentId: String? = null,
    val enabled: Boolean?,
    val roleIds: Set<String>,
    val password: String? = null,
)

/**
 * 修改用户显示名称的请求。
 *
 * @property displayName 新显示名称，去除首尾空白后须非空，最多 120 字符。
 */
data class UserProfileRequest(val displayName: String)

/**
 * 用户查询和资料更新共用的摘要。
 *
 * @property id 用户 ID。
 * @property username 账号的登录名。
 * @property displayName 界面展示的用户名称。
 * @property departmentId 所属部门 ID，未分配时为 `null`。
 * @property enabled 账号是否启用。
 */
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val enabled: Boolean,
)

/**
 * 用户详情及账号管理所需的角色关联。
 *
 * @property user 用户的基本信息。
 * @property roleIds 已分配的角色 ID，按 ID 升序排列，包含停用角色。
 */
data class UserDetails(val user: UserSummary, val roleIds: List<String>)

/**
 * 分配给用户的单个角色摘要。
 *
 * @property id 角色 ID。
 * @property code 角色的唯一编码。
 * @property name 角色的显示名称。
 */
data class AssignedRole(val id: String, val code: String, val name: String)

/**
 * 用户列表中的条目，附带可直接展示的角色信息。
 *
 * @property user 用户的基本信息。
 * @property roles 按编码升序排列的已分配角色，包含停用角色，无分配时为空列表。
 */
data class UserListEntry(val user: UserSummary, val roles: List<AssignedRole>)
