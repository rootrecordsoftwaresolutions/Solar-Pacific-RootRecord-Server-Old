plugins {
    java
}

version = "1.8.113"
repositories {
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly(project(":plugins:root-essentials"))
    compileOnly(project(":plugins:root-economy"))
    compileOnly(project(":plugins:root-claims"))
    compileOnly(project(":plugins:root-chestshops"))
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.26.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
