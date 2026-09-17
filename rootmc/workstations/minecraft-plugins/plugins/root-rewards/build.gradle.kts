plugins {
    java
}

version = "1.8.112"
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly(project(":plugins:rootmc"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
