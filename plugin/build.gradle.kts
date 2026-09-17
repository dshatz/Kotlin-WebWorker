import org.gradle.kotlin.dsl.`java-gradle-plugin`
import org.gradle.kotlin.dsl.signing

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.jvm)
    alias(libs.plugins.publish)
    signing
}

val module = libs.self.plugin.map { it.module }
val libGroup = module.map { it.group }
val libName = module.map { it.name }
val libVersion = project.findProperty("version") as? String ?: "0.1.0-SNAPSHOT1"
group = libGroup
version = libVersion

gradlePlugin {
    val jsWorkers = plugins.create("jsWorkers") {
        id = "com.dshatz.jsworker"
        implementationClass = "com.dshatz.jsworker.Plugin"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}")
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
        name.set("Kotlin-WebWorker Plugin")
        description.set("Gradle plugin for Kotlin-WebWorker.")
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