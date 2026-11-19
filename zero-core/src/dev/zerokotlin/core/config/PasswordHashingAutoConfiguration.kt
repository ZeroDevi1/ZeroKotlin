package dev.zerokotlin.core.config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@AutoConfiguration
internal class PasswordHashingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder::class)
    fun systemPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)
}
