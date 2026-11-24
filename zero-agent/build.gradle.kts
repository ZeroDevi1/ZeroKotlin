plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":zero-core"))
    implementation(libs.agentscope.harness)
    implementation(libs.agentscope.model.openai)
}
