plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
}

application {
    applicationName = rootProject.name
    mainClass.set("io.ktor.server.netty.EngineMain")
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}

dependencies {
    implementation(project(":zero-core"))
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-routing-openapi")
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-body-limit")
    implementation("org.flywaydb:flyway-core")
    implementation("org.xerial:sqlite-jdbc")
    implementation(libs.mybatis.plus.extension)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.quartz-scheduler:quartz")
    implementation(libs.jsoup)
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("commons-logging:commons-logging")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
    if (providers.gradleProperty("withAgent").map { it.toBooleanStrict() }.getOrElse(false)) {
        runtimeOnly(project(":zero-agent"))
    }
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

tasks.test { useJUnitPlatform() }
