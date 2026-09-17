plugins {
    java
}

version = "1.8.111"
dependencies {
    compileOnly(project(":plugins:root-core"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
