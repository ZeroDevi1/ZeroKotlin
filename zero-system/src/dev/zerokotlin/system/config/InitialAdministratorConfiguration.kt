package dev.zerokotlin.system.config

import dev.zerokotlin.system.support.normalizeUsername
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * 选择空库管理员的初始化方式。Flyway 仅接收布尔开关。
 * @param configuredUsername 部署提供的初始用户名。
 * @param configuredPassword 部署提供的初始密码。
 */
@Configuration(proxyBeanMethods = false)
class InitialAdministratorConfiguration(
    @Value("\${zero.system.admin.username:admin}") configuredUsername: String,
    @Value("\${zero.system.admin.password:admin}") configuredPassword: String,
) {
    /** 兼容旧部署配置用两个空字符串表示未设置。 */
    private val useDefaults = configuredUsername.isEmpty() && configuredPassword.isEmpty()

    /** 首次登记的账号名；保存自定义用户名时由初始化器规范化。 */
    val username = if (useDefaults) "admin" else configuredUsername

    /** 首次登记的密码，仅在账号表为空时使用。 */
    val password = if (useDefaults) "admin" else configuredPassword

    /** 只有约定的默认凭据可以使用短初始密码。 */
    val isDefaultAccount = normalizeUsername(username) == "admin" && password == "admin"

    /** 自定义初始凭据或密码编码器由启动初始化器处理；V9 SQL 负责默认账号。 */
    @Bean
    fun initialAdministratorSeed(encoder: PasswordEncoder) = FlywayConfigurationCustomizer { configuration ->
        val skip = !isDefaultAccount || encoder.javaClass != BCryptPasswordEncoder::class.java
        configuration.placeholders(configuration.placeholders + ("zeroSkipDefaultAdmin" to skip.toString()))
    }
}
