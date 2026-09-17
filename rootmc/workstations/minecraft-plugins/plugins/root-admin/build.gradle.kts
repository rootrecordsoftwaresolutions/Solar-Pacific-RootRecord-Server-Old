plugins {
    java
}

version = "1.8.111"
dependencies {
    compileOnly(project(":plugins:root-essentials"))
    compileOnly("net.kyori:adventure-text-serializer-plain:4.26.1")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
