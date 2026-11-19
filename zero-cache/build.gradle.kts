plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.data:spring-data-redis")
    implementation("io.lettuce:lettuce-core")
    implementation("tools.jackson.module:jackson-module-kotlin")
}
