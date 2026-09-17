val androidSdkHome = "/home/rootrecord/.local/opt/android-sdk"
val localPropertiesFile = file("local.properties")
val sdkDirLine = "sdk.dir=$androidSdkHome"
if (!localPropertiesFile.exists()) {
    localPropertiesFile.writeText("$sdkDirLine\n")
} else {
    val lines = localPropertiesFile.readText().lines()
    val current = lines.firstOrNull { it.trim().startsWith("sdk.dir=") }?.trim()
    if (current != sdkDirLine) {
        val next = if (current == null) {
            listOf(sdkDirLine) + lines
        } else {
            lines.map { if (it.trim().startsWith("sdk.dir=")) sdkDirLine else it }
        }
        localPropertiesFile.writeText(next.joinToString("\n").trimEnd() + "\n")
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KilaueaAlerts"
include(":app")
