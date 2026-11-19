package dev.zerokotlin.core.dto

data class KafkaPosition(val partition: Int, val offset: Long)

data class KafkaMessage(val partition: Int, val offset: Long, val key: String?, val value: String?)
