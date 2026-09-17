plugins {
    java
}

version = "1.8.114"
repositories {
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.bluecolored.de/releases")
}

dependencies {
    implementation("com.mysql:mysql-connector-j:9.2.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("de.bluecolored:bluemap-api:2.7.7")
    compileOnly(project(":plugins:rootmc"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
