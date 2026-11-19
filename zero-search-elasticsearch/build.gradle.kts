plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":zero-core"))
    api(platform(libs.spring.boot.bom))
    api("tools.jackson.core:jackson-databind")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-health")
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.security:spring-security-core")
}
