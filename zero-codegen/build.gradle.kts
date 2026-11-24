plugins {
    application
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")
}

application {
    mainClass.set("dev.zerokotlin.codegen.CodegenCliKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}
