plugins {
    java
}

version = "1.8.111"
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.mysql:mysql-connector-j:9.2.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
