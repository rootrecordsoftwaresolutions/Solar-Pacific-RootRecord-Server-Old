plugins {
    java
}

version = "1.8.111"
repositories {
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly(project(":plugins:root-core"))
    compileOnly(project(":plugins:rootmc"))
    compileOnly(project(":plugins:root-times"))
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.mysql:mysql-connector-j:9.2.0")
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    exclude("com/rootrecord/minecraft/common/**")
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.contains("mysql") }
            .map { zipTree(it) }
    })
}
