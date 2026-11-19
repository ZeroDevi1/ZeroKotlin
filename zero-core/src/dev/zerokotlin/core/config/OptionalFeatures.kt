package dev.zerokotlin.core.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource

/** JPA 变体的类路径标记与 Hibernate 均存在时，启用共享数据源上的 JPA 配置。 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnResource(resources = ["classpath:META-INF/zerokotlin/jpa.properties"])
@ConditionalOnClass(name = ["org.hibernate.cfg.SchemaToolingSettings"])
internal annotation class ConditionalOnZeroJpa

/** 搜索变体的标记存在且 `zero.search.enabled` 为 `true` 时，注册搜索组件及权限迁移。开关默认关闭。 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnResource(resources = ["classpath:META-INF/zerokotlin/elasticsearch.properties"])
@ConditionalOnProperty(prefix = "zero.search", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnZeroSearch

/** Kafka 变体的标记与客户端类均存在，且 `zero.kafka.enabled` 为 `true` 时，注册消息组件及权限迁移。开关默认关闭。 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnResource(resources = ["classpath:META-INF/zerokotlin/kafka.properties"])
@ConditionalOnClass(name = ["org.apache.kafka.clients.producer.KafkaProducer"])
@ConditionalOnProperty(prefix = "zero.kafka", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnZeroKafka
