package dev.zerokotlin.example

import dev.zerokotlin.core.ModuleMigration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ExampleConfiguration {
    @Bean
    fun exampleMigrations() = ModuleMigration("example")
}
