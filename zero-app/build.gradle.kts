import org.springframework.boot.gradle.tasks.aot.ProcessAot

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graal.native)
}

val enabledExtensions = mapOf(
    "withExample" to ":modules:example",
    "withSearch" to ":zero-search-elasticsearch",
    "withKafka" to ":zero-messaging-kafka",
).filterKeys { flag ->
    providers.gradleProperty(flag).map { it.toBooleanStrict() }.getOrElse(false)
}

dependencies {
    implementation(project(":zero-core"))
    implementation(project(":zero-system"))
    implementation(project(":zero-data-mybatis"))
    implementation(project(":zero-cache"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(kotlin("reflect"))
    implementation(libs.springdoc.ui)
    enabledExtensions.values.forEach { implementation(project(it)) }
}

tasks.withType<ProcessAot>().configureEach {
    args("--zero.database.mode=sqlite", "--zero.cache.mode=caffeine")
}

graalvmNative {
    binaries.named("main") {
        imageName.set("zero-kotlin")
    }
}
