plugins {
    java
}

version = "1.8.111"
repositories {
    maven("https://jitpack.io")
}

dependencies {
    // Folder + YAML helpers only â€” bundled into each plugin jar.
    if (rootProject.file("server/libraries/io/papermc/paper/paper-api/${property("paperApiBuild")}/paper-api-${property("paperApiBuild")}.jar").isFile) {
        compileOnly(files(rootProject.file("server/libraries/io/papermc/paper/paper-api/${property("paperApiBuild")}/paper-api-${property("paperApiBuild")}.jar")))
    } else {
        compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}
