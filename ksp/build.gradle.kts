plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
    `maven-publish`
    signing
}

val module = libs.self.ksp.map { it.module }
val libName = module.map { it.name }
val libGroup = module.map { it.group }

version = libVersion
group = libGroup

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(libs.ksp)
            implementation(libs.kotlinpoet)
            implementation(libs.kotlinpoet.ksp)
        }
    }
}

tasks.withType<Test>().configureEach {
    outputs.upToDateWhen { false }
    failOnNoDiscoveredTests = false
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
        name.set("Kotlin-WebWorker KSP")
        description.set("KSP Generator for Kotlin-WebWorker.")
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