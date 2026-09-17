plugins {
    java
}

description = "Copy this folder to plugins/<your-plugin-name> and customize."

dependencies {
    implementation(project(":plugins:rootrecord-common"))
}

tasks.named<Jar>("jar") {
    from(project(":plugins:rootrecord-common").extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java).sourceSets.getByName("main").output)
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
