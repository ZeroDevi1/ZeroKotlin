import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.graal.native) apply false
}

val bootBom = libs.spring.boot.bom
val kotlinBom = libs.kotlin.bom

subprojects {
    group = "dev.zerokotlin"
    version = "0.1.0-SNAPSHOT"

    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                javaParameters.set(true)
                freeCompilerArgs.add("-Xannotation-default-target=param-property")
            }
        }
        dependencies {
            add("implementation", platform(bootBom))
            add("implementation", platform(kotlinBom))
        }
    }
}
