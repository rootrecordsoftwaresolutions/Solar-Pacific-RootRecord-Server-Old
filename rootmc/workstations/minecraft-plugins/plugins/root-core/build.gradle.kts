plugins {
    java
}

version = "1.8.123"
dependencies {
    // Shared MySQL helper surface for operators probing connectivity later; Core does not open pools on enable.
    implementation("com.mysql:mysql-connector-j:9.2.0")
    // Discord chat + Slack server-logs (absorbed from Root-Discord).
    implementation("net.dv8tion:JDA:6.4.2") {
        exclude(module = "opus-java")
        exclude(module = "tink")
    }
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}
