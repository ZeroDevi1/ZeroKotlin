package dev.zerokotlin.core.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Kafka 示例发布接口接收的 JSON 请求体。
 *
 * @property key 可选消息键，省略时为 `null`。
 * @property value 待发送的文本值，由 Kafka 字符串序列化器编码。
 */
@Schema(description = "Kafka 示例发布接口接收的 JSON 请求体。")
data class PublishKafkaMessage(
    @field:Schema(description = "可选消息键，省略时为 `null`。")
    val key: String? = null,
    @field:Schema(description = "待发送的文本值，由 Kafka 字符串序列化器编码。")
    val value: String,
)
