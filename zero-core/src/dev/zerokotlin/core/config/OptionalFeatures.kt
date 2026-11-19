package dev.zerokotlin.core.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource

/** 打包 JPA 变体且存在 Hibernate 时，启用共享数据源上的 JPA 配置。 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnResource(resources = ["classpath:META-INF/zerokotlin/jpa.properties"])
@ConditionalOnClass(name = ["org.hibernate.cfg.SchemaToolingSettings"])
internal annotation class ConditionalOnZeroJpa

/** 打包搜索变体且开启 `zero.search.enabled` 时，注册搜索客户端、接口及权限迁移。 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnResource(resources = ["classpath:META-INF/zerokotlin/elasticsearch.properties"])
@ConditionalOnProperty(prefix = "zero.search", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnZeroSearch

/** 打包 Kafka 变体、客户端可用且开启 `zero.kafka.enabled` 时，注册消息组件及权限迁移。 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnResource(resources = ["classpath:META-INF/zerokotlin/kafka.properties"])
@ConditionalOnClass(name = ["org.apache.kafka.clients.producer.KafkaProducer"])
@ConditionalOnProperty(prefix = "zero.kafka", name = ["enabled"], havingValue = "true")
annotation class ConditionalOnZeroKafka
