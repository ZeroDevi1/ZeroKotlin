package dev.zerokotlin.core.config

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/** 在应用未提供密码编码器时注册系统默认实现。 */
@AutoConfiguration
internal class PasswordHashingAutoConfiguration {
    /**
     * 创建用于密码哈希和校验的默认编码器。
     *
     * @return 使用成本因子 12 的 BCrypt 密码编码器。
     */
    @Bean
    @ConditionalOnMissingBean(PasswordEncoder::class)
    fun systemPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)
}
