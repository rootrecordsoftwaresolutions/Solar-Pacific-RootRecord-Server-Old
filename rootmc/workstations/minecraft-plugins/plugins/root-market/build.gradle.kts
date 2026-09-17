plugins {
    java
}

version = "1.8.111"
dependencies {
    compileOnly(project(":plugins:root-chestshops"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
