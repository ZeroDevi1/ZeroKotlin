package dev.zerokotlin.nativeimage

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ZeroNativeHints::class)
class ZeroNativeConfiguration

class ZeroNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("db/migration/common/**")
        hints.resources().registerPattern("db/migration/sqlite/**")
        hints.resources().registerPattern("db/modules/**")
        hints.resources().registerPattern("mapper/**")
    }
}
