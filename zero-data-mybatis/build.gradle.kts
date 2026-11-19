plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":zero-core"))
    val mybatisPlusVersion = libs.versions.mybatis.plus.get()
    api("com.baomidou:mybatis-plus-spring-boot4-starter:$mybatisPlusVersion")
    implementation("com.baomidou:mybatis-plus-jsqlparser:$mybatisPlusVersion")
    implementation("org.springframework.boot:spring-boot-starter-flyway")

    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")
}
