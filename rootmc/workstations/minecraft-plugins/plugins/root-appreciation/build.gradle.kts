plugins {
    java
}

version = "1.8.121"
dependencies {
    compileOnly(project(":plugins:root-core"))
    compileOnly(project(":plugins:root-economy"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
