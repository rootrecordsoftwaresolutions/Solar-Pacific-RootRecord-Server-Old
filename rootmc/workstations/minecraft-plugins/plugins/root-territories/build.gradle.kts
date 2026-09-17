plugins {
    java
}

version = "1.8.111"
repositories {
    maven("https://repo.bluecolored.de/releases")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("de.bluecolored:bluemap-api:2.7.7")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
