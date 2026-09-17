package com.dshatz.jsworker

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.file.DefaultFilePropertyFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinWasmTargetAttribute
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.ir.DefaultIncrementalSyncTask
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.tasks.IncrementalSyncTask
import java.io.File
import kotlin.jvm.java

class Plugin: Plugin<Project> {

    override fun apply(target: Project) {
        val kmp = target.extensions.getByType(KotlinMultiplatformExtension::class.java)

        val outputJs = target.createJsWorkerConfiguration(false)
        val inputJs = target.createJsWorkerConfiguration(true)

        val outputWasmJs = target.createWasmJsWorkerConfiguration(false)
        val inputWasmJs = target.createWasmJsWorkerConfiguration(true)

        kmp.targets.matching {
            it.platformType == KotlinPlatformType.js
        }.configureEach {
            target.wireConfigurations("js", inputJs, outputJs)
        }

        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            kmp.targets.matching {
                it.platformType == KotlinPlatformType.wasm
            }.configureEach {
                target.wireConfigurations("wasmJs", inputWasmJs, outputWasmJs)
            }
        }

    }


    private fun Project.wireConfigurations(
        target: String, // js or wasmJs
        input: Configuration,
        output: Configuration
    ) {
        // Retrieve dependencies from incoming configuration:

        // 1. Add to executable resources.
        tasks.withType(ProcessResources::class.java).configureEach {
            if (it.name in setOf("${target}ProcessResources", "${target}TestProcessResources")) {
                it.dependsOn(input)
                it.from(input) {
                    it.into("workers")
                }
            }
        }

        // 2. Add to test webpack root.
        tasks.matching {
            it.name == "prepareWebpackBundleForKotlin${target.capitalize()}Tests"
        }.configureEach {
            it.dependsOn(input)
            it.doLast { task ->
                val workerDir = input.singleFile
                val bundleDir = (task.property("outputBundleDir") as DefaultFilePropertyFactory.DefaultDirectoryVar).get()
                workerDir.copyRecursively(bundleDir.dir("workers").asFile, overwrite = true)
            }
        }

    }

    private fun Project.createJsWorkerConfiguration(
        resolvable: Boolean
    ): Configuration {
        val name = "jsWorker" + if (!resolvable) "Output" else ""
        return configurations.create(name) {
            it.isCanBeConsumed = !resolvable
            it.isCanBeResolved = resolvable
            it.attributes {
                it.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category::class.java, Category.LIBRARY)
                )
                it.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
                )
                it.attribute(
                    KotlinPlatformType.attribute,
                    KotlinPlatformType.js
                )
            }
        }
    }

    private fun Project.createWasmJsWorkerConfiguration(
        resolvable: Boolean
    ): Configuration {
        val name = "wasmJsWorker" + if (!resolvable) "Output" else ""
        return configurations.create(name) {
            it.isCanBeConsumed = !resolvable
            it.isCanBeResolved = resolvable
            it.attributes {
                it.attribute(
                    Category.CATEGORY_ATTRIBUTE,
                    objects.named(Category::class.java, Category.LIBRARY)
                )
                it.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage::class.java, Usage.JAVA_RUNTIME)
                )
                it.attribute(
                    KotlinPlatformType.attribute,
                    KotlinPlatformType.wasm
                )
                it.attribute(
                    KotlinWasmTargetAttribute.wasmTargetAttribute,
                    KotlinWasmTargetAttribute.js
                )
            }
        }
    }
}

fun KotlinTarget.addOutgoingWorker(
    outputModuleName: Provider<String>
) {
    project.pluginManager.withPlugin("com.google.devtools.ksp") {
        val ksp = project.extensions.getByType(KspExtension::class.java)

        val outputJsFileName = outputModuleName.map { "$it.js" }
        ksp.arg("_jsworker_filename", outputJsFileName)

        val distTaskName = "${name}BrowserDistribution"

        val copyTaskName = "copyWorkerDistribution${name.capitalize()}"
        val copyTask = project.tasks.register(copyTaskName, Copy::class.java) {
            it.destinationDir = project.layout.buildDirectory.dir("workerDistribution${name.capitalize()}").get().asFile
            it.rename {
                if (it.endsWith(".js")) outputJsFileName.get()
                else null
            }
            val distTask = project.tasks.named(distTaskName)
            it.from(distTask)
            it.dependsOn(distTask)
        }

        val output = project.configurations.getByName("${name}WorkerOutput")
        project.artifacts {
            it.add(output.name, copyTask.map { it.destinationDir }) { artifact ->
                artifact.builtBy(copyTask)
            }
        }
    }
}

fun KotlinJsTargetDsl.asWebWorker() {
    addOutgoingWorker(outputModuleName)
}