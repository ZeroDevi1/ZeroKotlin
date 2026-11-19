package dev.zerokotlin.system.config

import dev.zerokotlin.core.ModuleMigration
import dev.zerokotlin.core.config.ConditionalOnZeroKafka
import dev.zerokotlin.core.config.ConditionalOnZeroSearch
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 将已启用的扩展权限脚本加入系统迁移，使用与对应客户端一致的装配条件。 */
@Configuration(proxyBeanMethods = false)
class SystemExtensionMigrations {
    /** @return 名为 `search` 的独立权限迁移。 */
    @Bean
    @ConditionalOnZeroSearch
    fun searchMigrations() = ModuleMigration("search")

    /** @return 名为 `kafka` 的独立权限迁移。 */
    @Bean
    @ConditionalOnZeroKafka
    fun kafkaMigrations() = ModuleMigration("kafka")
}
