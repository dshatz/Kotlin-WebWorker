@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
    alias(libs.plugins.serial)
    `maven-publish`
    signing
}

val module = libs.self.workers.map { it.module }
val libGroup = module.map { it.group }
val libName = module.map { it.name }

group = libGroup
version = libVersion

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    js {
        browser()
        nodejs()
    }
    wasmJs() {
        browser()
        nodejs()
    }

    sourceSets {
        webMain.dependencies {
            api(libs.browser)
            api(libs.coroutines.core)
            api(libs.serial)
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = libGroup.get(),
        artifactId = libName.get(),
        version = libVersion
    )

    pom {
        name.set("Kotlin-WebWorker Workers")
        description.set("Annotations and supporting code for Kotlin-WebWorker KSP processor.")
        url.set("https://github.com/dshatz/Kotlin-WebWorker")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        scm {
            url.set("https://github.com/dshatz/Kotlin-WebWorker")
            connection.set("scm:git:git://github.com/dshatz/Kotlin-WebWorker")
            developerConnection.set("scm:git:git://github.com/dshatz/Kotlin-WebWorker.git")
        }

        developers {
            developer {
                id.set("dshatz")
                name.set("Daniels Šatcs")
                url.set("https://github.com/dshatz")
            }
        }
    }
}

tasks.named { it == "commonizeNativeDistribution" }
    .configureEach { dependsOn("downloadKotlinNativeDistribution") }

tasks.named { it == "downloadKotlinNativeDistribution" }
    .configureEach { outputs.cacheIf { false } }
