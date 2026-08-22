import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
}

kotlin {
    jvmToolchain(17)
}

val cap4kAnalysisCompiler by configurations.creating

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(project(":domain"))
    implementation(project(":contract"))
    implementation(libs.cap4k.ddd.core)
    compileOnly(libs.cap4k.analysis.metadata)
    cap4kAnalysisCompiler(libs.cap4k.analysis.compiler)
    implementation(libs.cap4k.ddd.domain.repo.jpa)
    implementation(libs.jakarta.validation)
    implementation(libs.jakarta.persistence)
    implementation(libs.spring.context)
    implementation(libs.spring.data.jpa)

    testImplementation(kotlin("test"))
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
