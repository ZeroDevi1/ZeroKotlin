package dev.zerokotlin.system.dto

import io.swagger.v3.oas.annotations.media.Schema

/** 实时消息正文去除首尾空白后的长度上限，单位为 UTF-16 代码单元。 */
internal const val REALTIME_BODY_MAX_LENGTH = 2_000

/**
 * 实时群发请求。
 *
 * @property body 消息正文，去除首尾空白后须非空，最多 2 000 字符。
 */
@Schema(description = "实时群发请求。")
data class RealtimeBroadcastRequest(
    @field:Schema(description = "消息正文，去除首尾空白后须非空。", maxLength = REALTIME_BODY_MAX_LENGTH)
    val body: String,
)

/**
 * 指定接收用户的实时私发请求。
 *
 * @property recipientId 目标用户的 ID。
 * @property body 消息正文，去除首尾空白后须非空，最多 2 000 字符。
 */
@Schema(description = "实时私发请求。")
data class RealtimePrivateRequest(
    @field:Schema(description = "启用的目标用户 ID。", maxLength = 36)
    val recipientId: String,
    @field:Schema(description = "消息正文，去除首尾空白后须非空。", maxLength = REALTIME_BODY_MAX_LENGTH)
    val body: String,
)

/**
 * 当前实例已建立 STOMP 连接的在线用户统计。
 *
 * @property count 按用户 ID 去重后的连接用户数。
 */
@Schema(description = "当前实例的实时在线人数。")
data class RealtimePresence(
    @field:Schema(description = "已建立 STOMP 连接的用户数。同一用户的多个连接只计一次。", minimum = "0")
    val count: Int,
)

/**
 * 由服务端发送到 STOMP 订阅地址的实时消息。
 *
 * @property id 消息 ID。
 * @property senderId 从发送者会话读取的用户 ID。
 * @property senderName 发送时的用户显示名。
 * @property body 校验后的纯文本正文。
 * @property sentAt 发送时的 Unix 毫秒时间戳。
 */
data class RealtimeMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val sentAt: Long,
)
