package dev.zerokotlin.core.dto

/**
 * Kafka 示例发布接口接收的 JSON 请求体。
 *
 * @property key 可选消息键，省略时为 `null`。
 * @property value 待发送的文本值，由 Kafka 字符串序列化器编码。
 */
data class PublishKafkaMessage(val key: String? = null, val value: String)
