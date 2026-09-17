import org.gradle.api.Project

val Project.libVersion: String
    get() = project.property("version").toString()