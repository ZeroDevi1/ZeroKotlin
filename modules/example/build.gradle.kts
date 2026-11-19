plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":zero-core")) {
        capabilities { requireCapability("dev.zerokotlin:zero-core-jpa") }
    }
}
