import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

kotlin {
    jvmToolchain(17)
}

val cap4kAnalysisCompiler by configurations.creating

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(libs.cap4k.ddd.core)
    compileOnly(libs.cap4k.analysis.metadata)
    cap4kAnalysisCompiler(libs.cap4k.analysis.compiler)
    implementation(libs.cap4k.ddd.domain.repo.jpa)
    implementation(libs.jakarta.persistence)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.spring.context)
    implementation(libs.spring.data.jpa)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<KotlinCompile>("compileKotlin") {
    compilerOptions.freeCompilerArgs.addAll(providers.provider {
        cap4kAnalysisCompiler.resolve().map { "-Xplugin=${it.absolutePath}" }
    })
}

tasks.test {
    useJUnitPlatform()
}
