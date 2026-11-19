plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":zero-core"))
    implementation(project(":zero-data-jpa"))
    implementation("org.springframework:spring-webmvc")
    implementation("org.springframework.security:spring-security-core")
}
