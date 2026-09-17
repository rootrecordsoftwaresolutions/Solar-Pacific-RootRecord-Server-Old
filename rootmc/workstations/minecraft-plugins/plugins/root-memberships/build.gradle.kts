plugins {
    java
}

version = "1.8.114"
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(project(":plugins:root-core"))
    compileOnly(project(":plugins:rootmc"))
    compileOnly(project(":plugins:root-perms"))
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
