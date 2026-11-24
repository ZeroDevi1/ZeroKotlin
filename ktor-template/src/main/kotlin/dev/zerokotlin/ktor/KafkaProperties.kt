package dev.zerokotlin.ktor

import java.time.Duration

class KafkaProperties(
    val bootstrapServers: List<String> = listOf("localhost:9092"),
    val topic: String = "zero-example",
    val groupId: String = "zero-example",
    val timeout: Duration = Duration.ofSeconds(8),
    val clientProperties: Map<String, String> = emptyMap(),
) {
    init {
        require(bootstrapServers.isNotEmpty() && bootstrapServers.none(String::isBlank)) {
            "zero.kafka.bootstrap-servers must contain at least one broker address"
        }
        require(topic.matches(Regex("[a-zA-Z0-9._-]{1,249}")) && topic !in setOf(".", "..")) {
            "zero.kafka.topic must be a Kafka topic name"
        }
        require(groupId.isNotBlank()) { "zero.kafka.group-id must not be blank" }
        require(timeout in Duration.ofSeconds(1)..Duration.ofSeconds(30)) {
            "zero.kafka.timeout must be between 1 and 30 seconds"
        }
    }
}
