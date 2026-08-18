pluginManagement {
    val cap4kLocalPath = System.getenv("CAP4K_LOCAL_PATH")?.trim()?.takeIf { it.isNotEmpty() }
    plugins {
        id("io.github.ldmoxeii.cap4k.pipeline") version if (cap4kLocalPath != null) "999.0.0-local" else "2.0.1"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

val cap4kLocalPath = System.getenv("CAP4K_LOCAL_PATH")?.trim()?.takeIf { it.isNotEmpty() }
if (cap4kLocalPath != null) {
    includeBuild(cap4kLocalPath)
}

rootProject.name = "cap4k-reference-payment"

include("contract")
include("domain")
include("application")
include("adapter")
include("start")
