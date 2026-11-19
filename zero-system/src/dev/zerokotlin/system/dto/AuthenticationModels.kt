package dev.zerokotlin.system.dto

import dev.zerokotlin.system.entity.DataScope
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 登录时提交的账号凭据和可选挑战。
 *
 * @property username 登录名，校验格式后统一转为小写。
 * @property password 用于验证账号的原始密码。
 * @property captchaId 验证码启用时必填，取自验证码签发响应。
 * @property captchaCode 验证码启用时必填，为图片中显示的六位字符。
 */
@Schema(description = "登录时提交的账号凭据。")
class LoginRequest(
    @field:Schema(description = "登录名，校验格式后统一转为小写。", example = "admin")
    val username: String,
    @field:Schema(description = "用于验证账号的原始密码。", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    val password: String,
    @field:Schema(description = "启用验证码时必填，取自验证码接口。", accessMode = Schema.AccessMode.WRITE_ONLY)
    val captchaId: String? = null,
    @field:Schema(description = "启用验证码时必填，填写图片中的六位字符，字母忽略大小写。", accessMode = Schema.AccessMode.WRITE_ONLY)
    val captchaCode: String? = null,
)

/**
 * 登录验证码的挑战 ID、图像及过期时间。
 *
 * @property id 随机生成的一次性挑战 ID。
 * @property imageBase64 Base64 编码的 PNG。
 * @property expiresAt 挑战到期的 Unix 毫秒时间戳。
 */
@Schema(description = "一次性登录验证码。")
data class LoginCaptcha(
    @field:Schema(description = "登录时提交的挑战 ID。", minLength = 43, maxLength = 43)
    val id: String,
    @field:Schema(description = "Base64 编码的 PNG，前端可加上 data:image/png;base64, 前缀展示。", format = "byte")
    val imageBase64: String,
    @field:Schema(description = "验证码过期时间，单位为 Unix 毫秒。", format = "int64")
    val expiresAt: Long,
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
