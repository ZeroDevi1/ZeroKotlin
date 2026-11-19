package dev.zerokotlin.core.dto

/**
 * Kafka 确认发布后返回的消息位置。
 *
 * @property partition 写入的主题分区编号。
 * @property offset 消息在该分区中的偏移量。
 */
data class KafkaPosition(val partition: Int, val offset: Long)

/**
 * 从 Kafka 轮询批次中取出的单条记录。
 *
 * @property partition 记录所属的主题分区编号。
 * @property offset 记录在该分区中的偏移量。
 * @property key 反序列化后的字符串键，原记录没有键时为 `null`。
 * @property value 反序列化后的字符串值。原始值为 Kafka 的空值时，此处为 `null`。
 */
data class KafkaMessage(val partition: Int, val offset: Long, val key: String?, val value: String?)
