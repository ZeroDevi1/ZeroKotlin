package dev.zerokotlin.system.config

import dev.zerokotlin.core.ModuleMigration
import dev.zerokotlin.core.config.ConditionalOnZeroKafka
import dev.zerokotlin.core.config.ConditionalOnZeroSearch
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 将已启用的扩展权限脚本加入系统迁移，使用与对应客户端一致的装配条件。 */
@Configuration(proxyBeanMethods = false)
class SystemExtensionMigrations {
    /**
     * 为已启用的搜索接口登记索引写入和查询权限。
     * @return 模块名为 `search` 的迁移声明，Flyway 据此选择脚本目录和历史表。
     */
    @Bean
    @ConditionalOnZeroSearch
    fun searchMigrations() = ModuleMigration("search")

    /**
     * 为已启用的 Kafka 接口登记消息发布和消费权限。
     * @return 模块名为 `kafka` 的迁移声明，Flyway 据此选择脚本目录和历史表。
     */
    @Bean
    @ConditionalOnZeroKafka
    fun kafkaMigrations() = ModuleMigration("kafka")
}
