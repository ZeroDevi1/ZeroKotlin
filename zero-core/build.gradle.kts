plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

/** 变体名同时决定 Gradle capability 的后缀和 Spring 检查的装配标记文件名。 */
val optionalFeatures: List<String> = listOf("jpa", "elasticsearch", "kafka")
optionalFeatures.forEach { feature ->
    // 变体携带装配标记与可选依赖，Kotlin 源码统一由主源集编译。
    val markerDirectory = layout.buildDirectory.dir("generated/feature-markers/$feature")
    val marker = tasks.register<WriteProperties>("write${feature.replaceFirstChar { it.uppercaseChar() }}Marker") {
        destinationFile.set(markerDirectory.map { it.file("META-INF/zerokotlin/$feature.properties") })
        property("feature", feature)
    }
    val variant = sourceSets.create(feature) {
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
        output.dir(mapOf("builtBy" to marker), markerDirectory)
    }
    kotlin.sourceSets.named(feature) { kotlin.setSrcDirs(emptyList<String>()) }
    java.registerFeature(feature) { usingSourceSet(variant) }
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
    api(libs.swagger.annotations)
    implementation(libs.springdoc.ui)
    implementation(libs.minio)
    implementation(libs.aliyun.oss)

    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    compileOnly("org.apache.kafka:kafka-clients")

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
