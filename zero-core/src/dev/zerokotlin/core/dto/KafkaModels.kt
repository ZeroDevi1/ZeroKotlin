package dev.zerokotlin.core.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Kafka 确认发布后返回的消息位置。
 *
 * @property partition 写入的主题分区编号。
 * @property offset 消息在该分区中的偏移量。
 */
@Schema(description = "Kafka 确认发布后返回的消息位置。")
data class KafkaPosition(
    @field:Schema(description = "写入的主题分区编号。")
    val partition: Int,
    @field:Schema(description = "消息在该分区中的偏移量。")
    val offset: Long,
)

/**
 * 从 Kafka 轮询批次中取出的单条记录。
 *
 * @property partition 记录所属的主题分区编号。
 * @property offset 记录在该分区中的偏移量。
 * @property key 反序列化后的字符串键，原记录没有键时为 `null`。
 * @property value 反序列化后的字符串值。原始值为 Kafka 的空值时，此处为 `null`。
 */
@Schema(description = "从 Kafka 轮询批次中取出的单条记录。")
data class KafkaMessage(
    @field:Schema(description = "记录所属的主题分区编号。")
    val partition: Int,
    @field:Schema(description = "记录在该分区中的偏移量。")
    val offset: Long,
    @field:Schema(description = "反序列化后的字符串键，原记录没有键时为 `null`。")
    val key: String?,
    @field:Schema(description = "反序列化后的字符串值。原始值为 Kafka 的空值时，此处为 `null`。")
    val value: String?,
)
