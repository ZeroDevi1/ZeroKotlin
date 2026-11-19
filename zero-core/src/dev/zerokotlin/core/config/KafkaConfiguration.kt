package dev.zerokotlin.core.config

import dev.zerokotlin.core.controller.KafkaExampleController
import dev.zerokotlin.core.service.KafkaMessages
import dev.zerokotlin.core.service.kafkaFailureDetail
import java.time.Duration
import org.apache.kafka.common.KafkaException
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

/**
 * `zero.kafka` 下的连接、消息主题与消费组配置。
 *
 * @property bootstrapServers 用于发现集群的代理地址列表，默认仅含 `localhost:9092`。列表不能为空，且每项须为非空白地址。
 * @property topic 发布、消费和健康探测共用的主题，默认 `zero-example`。长度为 1 至 249，
 *   允许英文字母、数字、点、下划线和连字符，单独的 `.` 与 `..` 不可用。
 * @property groupId 提交消费偏移量的消费组，默认 `zero-example`，须为非空白字符串。
 * @property timeout 单次轮询、提交偏移量和等待异步结果各自使用的超时，默认 8 秒，范围为 1 至 30 秒。
 *   客户端配置使用整数毫秒，单次代理请求超时取其一半并向下取整。
 * @property clientProperties 代理客户端附加属性，默认空，可用于 TLS 和 SASL。
 *   与模块内指定的 Kafka 客户端属性同名时，以模块内的值为准。
 */
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

/** Kafka 功能启用时注册消息服务、主题健康探测和示例接口。 */
@AutoConfiguration
@ConditionalOnZeroKafka
@EnableConfigurationProperties(KafkaProperties::class)
@Import(KafkaExampleController::class)
class KafkaConfiguration {
    /**
     * 装配 Kafka 示例使用的消息服务。
     *
     * @param properties 启用 Kafka 时绑定的 Kafka 配置。
     * @return 使用指定主题和消费组的消息服务。
     */
    @Bean
    fun kafkaMessages(properties: KafkaProperties): KafkaMessages = KafkaMessages(properties)

    /**
     * 通过已配置主题的元数据判断 Kafka 是否可用。
     *
     * @param messages 提供主题分区探测的消息服务。
     * @return 查询成功时报告 UP 并附上分区数，Kafka 请求失败时报告 DOWN 并附上异常类型的健康指示器。
     */
    @Bean
    fun kafkaHealthIndicator(messages: KafkaMessages): HealthIndicator = HealthIndicator {
        try {
            Health.up().withDetail("partitions", messages.partitionCount()).build()
        } catch (failure: KafkaException) {
            Health.down().withDetail("reason", kafkaFailureDetail(failure)).build()
        }
    }
}
