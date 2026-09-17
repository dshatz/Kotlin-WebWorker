@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalJsTestDsl::class,
    ExperimentalWasmDsl::class
)
import org.jetbrains.kotlin.gradle.ExperimentalJsTestDsl
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinTest
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serial)
    id("com.dshatz.jsworker")
}

/*val workerArtifactWasm by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "wasm")
        attribute(Attribute.of("org.jetbrains.kotlin.wasm.target", String::class.java), "js")
    }
}

val workerArtifactJs by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class, Usage.JAVA_RUNTIME))
        attribute(Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java), "js")
    }
}

tasks.withType<ProcessResources>().configureEach {
    if (name == "jsProcessResources" || name == "jsProcessTestResources") {
        dependsOn(workerArtifactJs)
        from(configurations["workerArtifactJs"]) {
            into("workers")
        }
    }
}

tasks.configureEach {
    if (name == "prepareWebpackBundleForKotlinJsTests") {
        dependsOn(workerArtifactJs)
        doLast {
            val workerDir = configurations.named("workerArtifactJs").get().singleFile
            val bundleDir = (this.property("outputBundleDir") as DefaultFilePropertyFactory.DefaultDirectoryVar).get()
            workerDir.copyRecursively(bundleDir.dir("workers").asFile, overwrite = true)
        }
    }
}*/

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    js {
        binaries.executable()
        browser {
            test {
                headless = false
                this.timeout = 10.minutes
                firefox()
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
            /*test {
                firefox()
            }*/
        }
    }


    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotest)
            implementation(kotlin("test"))
            implementation(libs.kotlin.reflect)
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
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

dependencies {
    "jsWorker"(project(":test-worker", configuration = "jsWorkerOutput"))
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