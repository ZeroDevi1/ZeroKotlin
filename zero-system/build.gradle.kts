import org.springframework.boot.gradle.tasks.aot.ProcessAot

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graal.native)
}

fun enabled(flag: String): Boolean =
    providers.gradleProperty(flag).map { it.toBooleanStrict() }.getOrElse(false)

dependencies {
    implementation(project(":zero-core"))
    if (enabled("withSearch")) {
        runtimeOnly(project(":zero-core")) {
            capabilities { requireCapability("dev.zerokotlin:zero-core-elasticsearch") }
        }
    }
    if (enabled("withKafka")) {
        runtimeOnly(project(":zero-core")) {
            capabilities { requireCapability("dev.zerokotlin:zero-core-kafka") }
        }
    }
}

tasks.withType<ProcessAot>().configureEach {
    args("--zero.database.mode=sqlite", "--zero.cache.mode=caffeine")
}

graalvmNative {
    binaries.named("main") {
        imageName.set("zero-kotlin")
    }
}
