plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(project(":zero-data-mybatis"))
    runtimeOnly("org.hibernate.orm:hibernate-community-dialects")
}
