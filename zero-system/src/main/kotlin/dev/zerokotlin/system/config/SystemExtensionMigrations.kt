package dev.zerokotlin.system.config

import dev.zerokotlin.core.ModuleMigration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class SystemExtensionMigrations {
    @Bean
    @ConditionalOnClass(name = ["dev.zerokotlin.core.config.ElasticsearchConfiguration"])
    @ConditionalOnProperty(prefix = "zero.search", name = ["enabled"], havingValue = "true")
    fun searchMigrations() = ModuleMigration("search")

    @Bean
    @ConditionalOnClass(name = ["dev.zerokotlin.core.config.KafkaConfiguration"])
    @ConditionalOnProperty(prefix = "zero.kafka", name = ["enabled"], havingValue = "true")
    fun kafkaMigrations() = ModuleMigration("kafka")
}
