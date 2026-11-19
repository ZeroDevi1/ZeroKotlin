package dev.zerokotlin.core.config

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.ImportRuntimeHints

@AutoConfiguration
@ImportRuntimeHints(ZeroNativeHints::class)
internal class ZeroNativeConfiguration

internal class ZeroNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("zero-core-defaults.properties")
        hints.resources().registerPattern("db/migration/common/**")
        hints.resources().registerPattern("db/migration/sqlite/**")
        hints.resources().registerPattern("db/modules/**")
        hints.resources().registerPattern("mapper/**")
    }
}
