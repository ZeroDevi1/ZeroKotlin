plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
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
}
