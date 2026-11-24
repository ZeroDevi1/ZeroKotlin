import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
}

val bootBom = libs.spring.boot.bom
val kotlinBom = libs.kotlin.bom

subprojects {
    group = "dev.zerokotlin"
    version = "0.1.0-SNAPSHOT"

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            sourceSets.named("main") { kotlin.setSrcDirs(listOf("src")) }
            sourceSets.named("test") { kotlin.setSrcDirs(listOf("test")) }
            compilerOptions {
                javaParameters.set(true)
                freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }
        extensions.configure<SourceSetContainer> {
            named("main") {
                java.setSrcDirs(listOf("src"))
                resources.setSrcDirs(listOf("resources"))
            }
            named("test") {
                java.setSrcDirs(listOf("test"))
                resources.setSrcDirs(listOf("test-resources"))
            }
        }
        dependencies {
            add("implementation", platform(bootBom))
            add("implementation", platform(kotlinBom))
        }
    }
}
