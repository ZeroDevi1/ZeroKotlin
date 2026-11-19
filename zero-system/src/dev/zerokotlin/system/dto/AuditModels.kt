package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 单次登录尝试的审计查询结果。
 *
 * @property id 审计记录 ID。
 * @property userId 已查到的用户 ID，未查找或未匹配到账号时为 `null`。
 * @property username 规范化后的登录名，输入格式无效时为 `null`。
 * @property successful 本次登录是否成功建立会话。
 * @property remoteAddress 请求对端地址，启用审计脱敏时最多保留前 3 个字符。
 * @property occurredAt 登录尝试时间，单位为 Unix 毫秒。
 * @property ipRegion 可选的离线 IP 归属地。
 */
@Schema(description = "单次登录尝试的审计查询结果。")
data class LoginAuditEntry(
    @field:Schema(description = "审计记录 ID。")
    val id: String,
    @field:Schema(description = "已查到的用户 ID，未查找或未匹配到账号时为 `null`。")
    val userId: String?,
    @field:Schema(description = "规范化后的登录名，输入格式无效时为 `null`。", example = "admin")
    val username: String?,
    @field:Schema(description = "本次登录是否成功建立会话。")
    val successful: Boolean,
    @field:Schema(description = "服务端记录的请求对端地址，启用审计脱敏时以星号遮盖敏感部分。")
    val remoteAddress: String,
    @field:Schema(
        description = "登录尝试时间，单位为 Unix 毫秒。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val occurredAt: Long,
    @field:Schema(description = "离线 IP 归属地，旧记录或无法定位的地址返回 null。")
    val ipRegion: String?,
)

/**
 * 单次系统操作的审计查询结果。
 *
 * @property id 审计记录 ID。
 * @property actorId 执行操作的用户 ID。
 * @property action 审计动作标识或记录该操作的权限编码。
 * @property targetId 被操作对象的标识，如用户 ID 或权限编码。
 * @property occurredAt 操作记录时间，单位为 Unix 毫秒。
 */
@Schema(description = "单次系统操作的审计查询结果。")
data class OperationAuditEntry(
    @field:Schema(description = "审计记录 ID。")
    val id: String,
    @field:Schema(description = "执行操作的用户 ID。")
    val actorId: String,
    @field:Schema(description = "审计动作标识或记录该操作的权限编码。")
    val action: String,
    @field:Schema(description = "被操作对象的标识，如用户 ID 或权限编码。")
    val targetId: String,
    @field:Schema(
        description = "操作记录时间，单位为 Unix 毫秒。",
        type = "integer",
        format = "int64",
        example = "1767225600000",
    )
    val occurredAt: Long,
)

/** 单次标注端点的请求审计，状态码与异常类型可同时存在。 */
@Schema(description = "标注端点的请求审计结果。")
data class RequestAuditEntry(
    @field:Schema(description = "审计记录 ID。")
    val id: String,
    @field:Schema(description = "已认证操作者的用户 ID。")
    val actorId: String?,
    @field:Schema(description = "审计注解声明的动作标识。", example = "monitor.read")
    val action: String,
    @field:Schema(description = "HTTP 请求方法。", example = "GET")
    val httpMethod: String,
    @field:Schema(description = "匹配的 MVC 路由模板。", example = "/api/system/monitor")
    val routePattern: String,
    @field:Schema(description = "HTTP 结果状态码。", example = "200")
    val httpStatus: Int,
    @field:Schema(description = "MVC 请求处理耗时，单位为毫秒。")
    val durationMs: Long,
    @field:Schema(description = "发生异常时的异常类型简单类名。")
    val exceptionType: String?,
    @field:Schema(description = "服务端记录的请求对端地址。")
    val remoteAddress: String,
    @field:Schema(description = "可选的离线 IP 归属地。")
    val ipRegion: String?,
    @field:Schema(description = "显式开启采集时记录的 User-Agent。")
    val userAgent: String?,
    @field:Schema(description = "请求开始处理的 Unix 毫秒时间。")
    val occurredAt: Long,
)
