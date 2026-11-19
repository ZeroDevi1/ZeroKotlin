package dev.zerokotlin.system.dto

import dev.zerokotlin.system.entity.DataScope

/**
 * 登录时提交的账号凭据。
 *
 * @property username 登录名，校验格式后统一转为小写。
 * @property password 用于验证账号的原始密码。
 */
class LoginRequest(val username: String, val password: String)

/**
 * 登录成功后签发的会话凭据。
 *
 * @property accessToken 用于后续请求认证的访问令牌。
 * @property tokenType 认证方案，当前为 `Bearer`。
 * @property expiresAt 会话到期时间，单位为 Unix 毫秒。
 */
data class LoginResponse(val accessToken: String, val tokenType: String, val expiresAt: Long)

/**
 * 当前会话用户的身份与有效授权。
 *
 * @property id 当前用户 ID。
 * @property username 账号的登录名。
 * @property displayName 界面展示的用户名称。
 * @property departmentId 所属部门 ID，未分配时为 `null`。
 * @property permissions 以权限编码为键、有效数据范围为值的映射。
 * @property administrator 是否拥有已启用的内置系统管理员角色。
 */
data class CurrentUserResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val departmentId: String?,
    val permissions: Map<String, DataScope>,
    val administrator: Boolean,
)
