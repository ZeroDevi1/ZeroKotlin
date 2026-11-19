plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":zero-core"))
    implementation("org.apache.kafka:kafka-clients")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-health")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.security:spring-security-core")
}
