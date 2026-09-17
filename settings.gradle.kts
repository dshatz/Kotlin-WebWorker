rootProject.name = "js-worker-kt"


includeBuild("plugin")
include(":workers")
include(":e2e")
include(":test-worker")
include(":ksp")


pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.develocity") version "4.3.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

develocity {
    buildScan {
        termsOfUseAgree.set("yes")
        termsOfUseUrl = "https://gradle.com/terms-of-service"
    }
}
