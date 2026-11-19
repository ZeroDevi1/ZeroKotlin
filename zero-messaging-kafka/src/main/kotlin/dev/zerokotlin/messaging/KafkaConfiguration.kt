package dev.zerokotlin.messaging

import dev.zerokotlin.core.ModuleMigration
import org.apache.kafka.common.KafkaException
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties("zero.kafka")
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

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "zero.kafka", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(KafkaProperties::class)
class KafkaConfiguration {
    @Bean
    fun kafkaMigrations() = ModuleMigration("kafka")

    @Bean
    fun kafkaMessages(properties: KafkaProperties): KafkaMessages = KafkaMessages(properties)

    @Bean
    fun kafkaHealthIndicator(messages: KafkaMessages): HealthIndicator = HealthIndicator {
        try {
            Health.up().withDetail("partitions", messages.partitionCount()).build()
        } catch (failure: KafkaException) {
            Health.down().withDetail("reason", kafkaFailureDetail(failure)).build()
        }
    }
}
