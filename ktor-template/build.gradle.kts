plugins {
    application
    id("io.ktor.plugin") version "3.6.1"
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

ktor {
    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
    }
}

// 在仓库中使用原有迁移；init 后同一套 SQL 已写入项目资源目录。
tasks.processResources {
    from("../zero-system/resources") {
        include("db/migration/**", "db/modules/**")
    }
}

dependencies {
    implementation(platform("io.ktor:ktor-bom:3.6.1"))
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-routing-openapi")
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-body-limit")
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.2"))
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("org.quartz-scheduler:quartz")
    implementation("org.apache.kafka:kafka-clients")
    implementation("org.xerial:sqlite-jdbc")
    implementation("org.apache.poi:poi-ooxml:6.0.0")
    implementation("io.minio:minio:9.0.3")
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.18.5")
    implementation("com.zaxxer:HikariCP")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("io.lettuce:lettuce-core")
    implementation("org.jsoup:jsoup:1.24.1")
    implementation("org.springframework.security:spring-security-crypto")
    runtimeOnly("org.apache.commons:commons-logging:1.3.5")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.38")
}

tasks.register<JavaExec>("codegen") {
    group = "application"
    description = "从数据库表生成 Ktor CRUD 路由"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.zerokotlin.codegen.CodegenCliKt")
    workingDir(projectDir)
}
