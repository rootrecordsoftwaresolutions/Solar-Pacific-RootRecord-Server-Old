plugins {
    java
}

version = "1.8.111"
dependencies {
    implementation("com.mysql:mysql-connector-j:9.2.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly(project(":plugins:root-core"))
}

repositories {
    maven("https://jitpack.io")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    // Do not embed rootrecord-common â€” Root-Core already ships it. Embedding causes
    // LinkageError when RootCoreApi.databaseSettings() returns Core's DatabaseSettings.
    exclude("com/rootrecord/minecraft/common/**")
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            // mysql connector only â€” never pull rootrecord-common jars into this plugin
            .filter { it.name.contains("mysql") }
            .map { zipTree(it) }
    })
}
