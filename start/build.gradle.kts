import java.net.InetAddress
import java.net.ServerSocket

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))

    implementation(project(":adapter"))
    implementation(libs.cap4k.ddd.jpa.starter)
    implementation(libs.cap4k.ddd.domain.event.jpa.starter)
    implementation(libs.cap4k.ddd.integration.event.http.starter)
    implementation(libs.cap4k.ddd.endpoint.http.starter)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)

    runtimeOnly(libs.h2)

    testImplementation(project(":contract"))
    testImplementation(project(":domain"))
    testImplementation(project(":application"))
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    doFirst {
        val receiverPort = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        systemProperty("payment.reference.test.integration-event-port", receiverPort)
        systemProperty(
            "cap4k.ddd.integration.event.http.routes[payment.merchant-settlement.completed.v1]",
            "http://127.0.0.1:$receiverPort",
        )
    }
}
