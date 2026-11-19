package dev.zerokotlin.core.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.support.ResourcePropertySource

/** 在应用环境初始化期间加载核心模块的默认属性。 */
internal class ZeroCoreDefaults : EnvironmentPostProcessor {
    /**
     * 将 `zero-core-defaults.properties` 追加到属性源末尾，允许优先级更高的配置覆盖。
     *
     * @param environment 接收默认属性的应用环境。
     * @param application Spring Boot 传入的应用实例，此实现仅使用环境。
     */
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        environment.propertySources.addLast(ResourcePropertySource("classpath:zero-core-defaults.properties"))
    }
}
