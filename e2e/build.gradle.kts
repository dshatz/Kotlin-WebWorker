@file:OptIn(ExperimentalKotlinGradlePluginApi::class,
    ExperimentalWasmDsl::class
)
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serial)
    id("com.dshatz.jsworker")
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    js {
        binaries.executable()
        browser {
            testTask {
                useKarma {
                    useFirefoxHeadless()
                }
            }
        }
    }
    wasmJs {
        binaries.executable()
        browser {
            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }
    }


    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotest)
            implementation(kotlin("test"))
//            implementation(libs.kotlin.reflect)
            implementation(libs.coroutines.test)
            implementation(npm("kotlin-web-helpers", "3.5.3"))
        }

        commonMain {
            dependencies {
                implementation(project(":workers"))
                implementation(project(":test-worker"))
                implementation(libs.coroutines.core)
            }
        }
    }
}

dependencies {
    "jsWorker"(project(":test-worker"))
    "wasmJsWorker"(project(":test-worker"))
}

tasks.withType<Test>().configureEach {
    outputs.upToDateWhen { false }
    failOnNoDiscoveredTests = false
}

tasks.withType<KotlinNativeTest>().configureEach {
    outputs.upToDateWhen { false }
    failOnNoDiscoveredTests = false
}

tasks.withType<KotlinTest>().configureEach {
    outputs.upToDateWhen { false }
    failOnNoDiscoveredTests = false
}