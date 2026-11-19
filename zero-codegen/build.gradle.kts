plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":zero-core"))
}

application {
    mainClass.set("dev.zerokotlin.codegen.CodegenCliKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}
