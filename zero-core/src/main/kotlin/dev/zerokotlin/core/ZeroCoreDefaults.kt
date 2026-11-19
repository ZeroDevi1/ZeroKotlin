package dev.zerokotlin.core

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.io.support.ResourcePropertySource

internal class ZeroCoreDefaults : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        environment.propertySources.addLast(ResourcePropertySource("classpath:zero-core-defaults.properties"))
    }
}
