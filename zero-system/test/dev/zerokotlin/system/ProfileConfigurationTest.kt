package dev.zerokotlin.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.mock.env.MockEnvironment

/** 只加载应用实际打包的配置文件，检查不同环境的默认策略。 */
class ProfileConfigurationTest {
    @Test
    fun `未指定环境时使用开发配置`() {
        verifyProfile(null, "data/zero-kotlin.db", captcha = false, docs = true)
    }

    @Test
    fun `生产配置隔离默认数据并启用登录验证码`() {
        verifyProfile("prod", "data/prod/zero-kotlin.db", captcha = true, docs = false)
    }

    private fun verifyProfile(profile: String?, sqlitePath: String, captcha: Boolean, docs: Boolean) {
        GenericApplicationContext().use { context ->
            val environment = MockEnvironment().withProperty("spring.config.location", "classpath:/application.yaml")
            if (profile != null) environment.setActiveProfiles(profile)
            context.environment = environment
            ConfigDataApplicationContextInitializer().initialize(context)
            assertEquals("auto", environment.getRequiredProperty("zero.database.mode"))
            assertEquals(sqlitePath, environment.getRequiredProperty("zero.database.sqlite.path"))
            assertEquals(captcha.toString(), environment.getRequiredProperty("zero.system.captcha.enabled"))
            assertEquals(docs.toString(), environment.getRequiredProperty("springdoc.api-docs.enabled"))
        }
    }
}
