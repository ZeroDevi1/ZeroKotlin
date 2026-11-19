package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 本人可编辑的完整资料。
 *
 * @property displayName 界面展示的名称。
 * @property email 邮箱，缺省或留空时清除。
 * @property mobile 手机号，缺省或留空时清除。
 */
data class OwnProfileRequest(
    @field:Schema(description = "显示名称，去除首尾空白后最多 120 字符。")
    val displayName: String,
    @field:Schema(description = "可选邮箱，最多 254 字符。", format = "email")
    val email: String? = null,
    @field:Schema(description = "可选手机号，接受 6～15 位数字及可选的国际前缀 +。")
    val mobile: String? = null,
)

/**
 * 当前用户的登录名和个人资料。
 *
 * @property username 登录名。
 * @property displayName 界面展示的名称。
 * @property email 邮箱，未设置时为 null。
 * @property mobile 手机号，未设置时为 null。
 */
data class OwnProfile(
    @field:Schema(description = "登录名。", accessMode = Schema.AccessMode.READ_ONLY)
    val username: String,
    @field:Schema(description = "显示名称。")
    val displayName: String,
    @field:Schema(description = "邮箱。", format = "email")
    val email: String?,
    @field:Schema(description = "手机号。")
    val mobile: String?,
)

/** 本人更换密码所需的旧密码和新密码。 */
class ChangePasswordRequest(
    @field:Schema(description = "当前密码。", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
    val oldPassword: String,
    @field:Schema(
        description = "新密码，至少 12 个 Unicode 码点，最多 72 个 UTF-8 字节。",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
    )
    val newPassword: String,
)

/** 管理员重置指定账号密码的请求。 */
class ResetPasswordRequest(
    @field:Schema(
        description = "新密码，至少 12 个 Unicode 码点，最多 72 个 UTF-8 字节。",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY,
    )
    val newPassword: String,
)

/** 在同一事务中删除一组资源的请求。 */
data class DeleteResourcesRequest(
    @field:Schema(description = "要删除的资源 ID 列表，1～100 项，重复 ID 合并。")
    val ids: List<String?>,
)
