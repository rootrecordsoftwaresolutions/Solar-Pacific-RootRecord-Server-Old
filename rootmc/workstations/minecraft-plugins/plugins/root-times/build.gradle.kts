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
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
