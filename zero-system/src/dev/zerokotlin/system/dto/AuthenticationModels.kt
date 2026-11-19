package dev.zerokotlin.system.dto

import dev.zerokotlin.system.entity.DataScope
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 登录时提交的账号凭据。
 *
 * @property username 登录名，校验格式后统一转为小写。
 * @property password 用于验证账号的原始密码。
 */
@Schema(description = "登录时提交的账号凭据。")
class LoginRequest(
    @field:Schema(description = "登录名，校验格式后统一转为小写。", example = "admin")
    val username: String,
    @field:Schema(description = "用于验证账号的原始密码。", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    val password: String,
)

/**
 * 登录成功后签发的会话凭据。
 *
 * @property accessToken 用于后续请求认证的访问令牌。
 * @property tokenType 认证方案，当前为 `Bearer`。
 * @property expiresAt 会话到期时间，单位为 Unix 毫秒。
 */
@Schema(description = "登录成功后签发的会话凭据。")
data class LoginResponse(
    @field:Schema(description = "用于后续请求认证的访问令牌。", accessMode = Schema.AccessMode.READ_ONLY)
    val accessToken: String,
    @field:Schema(description = "认证方案，当前为 `Bearer`。", example = "Bearer")
    val tokenType: String,
    @field:Schema(
        description = "会话到期时间，单位为 Unix 毫秒。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val expiresAt: Long,
)

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
@Schema(description = "当前会话用户的身份与有效授权。")
data class CurrentUserResponse(
    @field:Schema(description = "当前用户 ID。")
    val id: String,
    @field:Schema(description = "账号的登录名。", example = "admin")
    val username: String,
    @field:Schema(description = "界面展示的用户名称。", example = "王明")
    val displayName: String,
    @field:Schema(description = "所属部门 ID，未分配时为 `null`。")
    val departmentId: String?,
    @field:Schema(description = "以权限编码为键、有效数据范围为值的映射。")
    val permissions: Map<String, DataScope>,
    @field:Schema(description = "是否拥有已启用的内置系统管理员角色。")
    val administrator: Boolean,
)
