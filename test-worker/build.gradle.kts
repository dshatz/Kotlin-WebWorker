@file:OptIn(ExperimentalWasmDsl::class)

import com.dshatz.jsworker.asWebWorker
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serial)
    alias(libs.plugins.ksp)
    id("com.dshatz.jsworker")
}


kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    js {
        binaries.executable()
        browser()
        asWebWorker()
    }
    wasmJs {
        binaries.executable()
        browser()
        asWebWorker()
    }

    sourceSets {
        webMain.dependencies {
            implementation(project(":workers"))
        }
        /*jsMain.configure {
            resources.srcDir(project.layout.buildDirectory.dir("generated/ksp/js/jsMain/resources"))
        }*/
    }
}

dependencies {
    "kspJs"(project(":ksp"))
    "kspWasmJs"(project(":ksp"))
}