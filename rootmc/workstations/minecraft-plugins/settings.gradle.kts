pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// Do not set org.gradle.java.home here — Gradle 8.x cannot run on JDK 25.
// Plugin bytecode uses the JDK 25 toolchain via gradle.properties installations path.

rootProject.name = "rootrecord-minecraft"

// Starter template (copy into plugins/ for new work). Not loaded when developing real plugins.
include("plugin-template")

include("plugins:rootrecord-common")

// Auto-include each active plugin under plugins/ that has its own build.gradle.kts
file("plugins").listFiles()
    ?.filter {
        it.isDirectory
            && !it.name.startsWith(".")
            && it.name != "rootrecord-common"
            && file("${it.path}/build.gradle.kts").exists()
    }
    ?.forEach { include("plugins:${it.name}") }

// Halted plugins — isolated; build individually; never deployed by publishPlugins
file("halted-development").listFiles()
    ?.filter {
        it.isDirectory
            && !it.name.startsWith(".")
            && file("${it.path}/build.gradle.kts").exists()
    }
    ?.forEach {
        include("halted:${it.name}")
        project(":halted:${it.name}").projectDir = it
    }
