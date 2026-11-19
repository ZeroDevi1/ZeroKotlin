plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

val optionalFeatures: List<String> = listOf("jpa", "elasticsearch", "kafka")
java {
    optionalFeatures.forEach { feature ->
        registerFeature(feature) {
            usingSourceSet(sourceSets.create(feature))
        }
    }
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(platform(libs.kotlin.bom))
    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-cache")

    val mybatisPlusVersion = libs.versions.mybatis.plus.get()
    api("com.baomidou:mybatis-plus-spring-boot4-starter:$mybatisPlusVersion")
    implementation("com.baomidou:mybatis-plus-jsqlparser:$mybatisPlusVersion")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")

    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.data:spring-data-redis")
    implementation("io.lettuce:lettuce-core")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation(kotlin("reflect"))
    implementation(libs.springdoc.ui)

    // feature 依赖主变体，调用方选择 feature 时同时得到核心契约和运行时配置。
    optionalFeatures.forEach { feature ->
        add("${feature}Api", project(":zero-core"))
    }

    add("jpaApi", "org.springframework.boot:spring-boot-starter-data-jpa")
    add("jpaRuntimeOnly", "org.hibernate.orm:hibernate-community-dialects")
    add("elasticsearchApi", "tools.jackson.core:jackson-databind")
    add("elasticsearchImplementation", "org.springframework.boot:spring-boot-health")
    add("kafkaImplementation", "org.apache.kafka:kafka-clients")
    add("kafkaImplementation", "org.springframework.boot:spring-boot-health")
}
