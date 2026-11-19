package dev.zerokotlin.core.security

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

/** 只有编码成功才允许将凭证写入数据库。 */
fun PasswordEncoder.hashPassword(rawPassword: String): String =
    checkNotNull(encode(rawPassword)) { "Password encoder returned no hash" }
