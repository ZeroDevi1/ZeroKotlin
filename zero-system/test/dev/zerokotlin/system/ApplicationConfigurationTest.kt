package dev.zerokotlin.system

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 读取实际打包的 HOCON 配置，检查本地开发默认值。 */
class ApplicationConfigurationTest {
    @Test
    fun `默认使用回环地址 SQLite 回退并开放接口文档`() {
        val config = ConfigFactory.parseResources("application.conf").resolve(ConfigResolveOptions.noSystem())
        assertEquals("127.0.0.1", config.getString("ktor.deployment.host"))
        assertEquals("auto", config.getString("zero.databaseMode"))
        assertEquals("data/zero-ktor.db", config.getString("zero.databasePath"))
        assertEquals(false, config.getBoolean("zero.captchaEnabled"))
        assertEquals(true, config.getBoolean("zero.apiDocsEnabled"))
    }
}
