plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(platform(libs.ktor.bom))
    api(platform(libs.spring.boot.bom))
    api("io.ktor:ktor-server-core")
    api("com.zaxxer:HikariCP")
    api(libs.mybatis.plus.core)
    implementation(libs.mybatis.plus.jsqlparser)
    api("org.apache.kafka:kafka-clients")
    implementation("org.xerial:sqlite-jdbc")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("io.lettuce:lettuce-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
    implementation(libs.minio)
    implementation(libs.aliyun.oss)
    implementation(libs.apache.poi.ooxml)
    implementation(libs.hutool.all)
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test { useJUnitPlatform() }
