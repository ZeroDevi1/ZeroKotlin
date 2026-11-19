plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(project(":zero-core"))
    implementation(libs.agentscope.harness)
    implementation(libs.agentscope.openai.spring.boot.starter)
}
