plugins {
    java
}

version = "1.8.111"
dependencies {
    implementation("com.mysql:mysql-connector-j:9.2.0")
    compileOnly(project(":plugins:root-core"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    // Use Root-Core's embedded rootrecord-common at runtime (avoid LinkageError).
    exclude("com/rootrecord/minecraft/common/**")
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .filter { it.name.contains("mysql") }
            .map { zipTree(it) }
    })
}
