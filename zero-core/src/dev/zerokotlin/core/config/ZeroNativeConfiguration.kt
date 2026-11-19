package dev.zerokotlin.core.config

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.ImportRuntimeHints

/** 为 Spring AOT 装配核心模块的原生镜像资源提示。 */
@AutoConfiguration
@ImportRuntimeHints(ZeroNativeHints::class)
internal class ZeroNativeConfiguration

/** 声明原生镜像运行时所需的默认配置、功能标记、迁移脚本和 Mapper 资源。 */
internal class ZeroNativeHints : RuntimeHintsRegistrar {
    /**
     * 注册默认配置文件与资源路径模式，供原生镜像打包。
     *
     * @param hints 接收核心资源声明的提示注册表。
     * @param classLoader Spring 提供的可选类加载器。资源直接按路径模式注册。
     */
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("zero-core-defaults.properties")
        hints.resources().registerPattern("META-INF/zerokotlin/*.properties")
        hints.resources().registerPattern("db/migration/common/**")
        hints.resources().registerPattern("db/migration/sqlite/**")
        hints.resources().registerPattern("db/modules/**")
        hints.resources().registerPattern("mapper/**")
    }
}
