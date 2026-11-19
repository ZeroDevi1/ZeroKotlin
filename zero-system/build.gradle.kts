import org.springframework.boot.gradle.tasks.aot.ProcessAot

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.graal.native)
}

/**
 * 读取启动包的可选功能开关。
 *
 * @param flag 传给 Gradle 的项目属性名，例如 `-PwithKafka=true` 中的 `withKafka`。
 * @return 属性为 `true` 时装配对应变体，未设置或为 `false` 时跳过。
 * @throws IllegalArgumentException 配置值不是小写 `true` 或 `false`。
 */
fun enabled(flag: String): Boolean =
    providers.gradleProperty(flag).map { it.toBooleanStrict() }.getOrElse(false)

dependencies {
    implementation("org.springframework.boot:spring-boot-flyway")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    if (enabled("withAgent")) {
        runtimeOnly(project(":zero-agent"))
    }
}

tasks.test { useJUnitPlatform() }

tasks.withType<ProcessAot>().configureEach {
    args("--zero.database.mode=sqlite", "--zero.cache.mode=caffeine")
}

graalvmNative {
    binaries.named("main") {
        imageName.set("zero-kotlin")
    }
}
