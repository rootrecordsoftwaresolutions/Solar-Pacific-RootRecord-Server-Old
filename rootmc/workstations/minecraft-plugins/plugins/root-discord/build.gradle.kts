plugins {
    java
}

version = "1.8.111"
dependencies {
    compileOnly(project(":plugins:root-core"))
    implementation("net.dv8tion:JDA:6.4.2") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    // Use Root-Core's embedded rootrecord-common at runtime (avoid LinkageError).
    exclude("com/rootrecord/minecraft/common/**")
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
