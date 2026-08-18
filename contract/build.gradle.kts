import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

val cap4kAnalysisCompiler by configurations.creating

dependencies {
    implementation(libs.cap4k.contract.api)
    compileOnly(libs.cap4k.analysis.metadata)
    cap4kAnalysisCompiler(libs.cap4k.analysis.compiler)

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
