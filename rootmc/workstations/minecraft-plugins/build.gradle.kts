import groovy.json.JsonOutput
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File

val localProperties = java.util.Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val javaVersion = JavaLanguageVersion.of(
    (localProperties.getProperty("java.version") ?: findProperty("javaVersion") as String? ?: "25").toInt(),
)

// Prefer the API jar bundled with server/ (exact match to your Paper build).
val serverApiJar = rootProject.file(
    "server/libraries/io/papermc/paper/paper-api/${property("paperApiBuild")}/paper-api-${property("paperApiBuild")}.jar",
)
val paperApiMaven = "io.papermc.paper:paper-api:${property("paperApiVersion")}"
val serverPluginsDir = rootProject.layout.projectDirectory.dir("server/plugins")
val hostHandoffPluginsDir = rootProject.layout.projectDirectory.dir("server/host-handoff/plugins")
val webPluginsDir = rootProject.layout.projectDirectory.dir("../../Web Files/rootmc-web/public/plugins")
val webPluginsBuildDir = rootProject.layout.projectDirectory.dir("../../Web Files/rootmc-web/build/plugins")
val emergentWebPluginsDir = rootProject.layout.projectDirectory.dir("../../emergent-repo/web/public/plugins")
val workspaceHandoffPluginsDir: File = run {
    val fromProps = localProperties.getProperty("rootmc.workspace.handoff.plugins.dir")?.trim()
    when {
        !fromProps.isNullOrBlank() -> rootProject.file(fromProps)
        else -> rootProject.file("../../Server Handoffs/2. RootMC - Towny/plugins")
    }
}
val liveServerPluginsDir: File = run {
    val fromProps = localProperties.getProperty("rootmc.live.plugins.dir")?.trim()
    when {
        !fromProps.isNullOrBlank() -> rootProject.file(fromProps)
        else -> workspaceHandoffPluginsDir
    }
}

// Gen 1 26 (live production) â€” never deploy or prune into Gen 2 (gen2-27).
check(localProperties.getProperty("rootmc.generation")?.trim() != "gen2") {
    "Gen 1 workspace: set rootmc.generation=gen1 in local.properties (not gen2)."
}
fun assertGen1DeployTarget(dir: File, label: String) {
    val norm = dir.absolutePath.replace('\\', '/').lowercase()
    check(!norm.contains("/gen2-27/")) {
        "Gen 1 $label must not point at Gen 2 (gen2-27): ${dir.absolutePath}"
    }
    check(!norm.contains("/gen2/")) {
        "Gen 1 $label must not point at D:/Gen2: ${dir.absolutePath}"
    }
}
listOf(
    "workspace handoff" to workspaceHandoffPluginsDir,
    "live server" to liveServerPluginsDir,
).forEach { (label, dir) -> assertGen1DeployTarget(dir, label) }
val pluginPublishBaseUrl = "https://rootmc.net/plugins"

/** Not deployed; source may live under halted-development/ for optional one-off builds. */
val haltedPluginNames = setOf(
    "root-questionnaire",
    "root-ask",
    "root-contracts",
    "root-explore",
)

val haltedPluginConfigFiles = setOf(
    "root-questionnaire.yml",
    "root-ask.yml",
    "root-blueprints.yml",
    "root-contracts.yml",
    "root-explore.yml",
)

val retiredPluginNames = setOf(
    "root-chamber",
    // Absorbed by Root-Times
    "root-activity",
    // Absorbed by Root-Essentials
    "root-spawn",
    "root-banner",
    "root-potions",
    "rootmc-shops",
    "root-bonds",
    "root-upkeep",
    "root-loans",
    "root-tokens",
    // Absorbed by Root-Play
    "root-ranks",
    "root-rewards",
    "roothelp",
    // Absorbed by Root-Ops
    "root-admin",
    "root-restart",
    "root-announcer",
    "root-mapper",
    // Renamed to Root-Haste
    "root-joint",
    // Absorbed by Root-Core (comms)
    "root-discord",
) + haltedPluginNames

/** Built + staged to handoff/server only â€” never copied to rootmc.net/plugins or manifest. */
val privateServerOnlyPluginNames = setOf<String>()

/** Claims-land plugin — never stage into Towny FileZilla / host-handoff folders. */
val townyHandoffSkipPluginNames = setOf(
    "root-claims",
)

fun isTownyHandoffPluginsDir(dir: File): Boolean {
    val norm = dir.absolutePath.replace('\\', '/').lowercase()
    return norm.contains("/2. rootmc - towny/plugins")
        || norm.contains("/server/host-handoff/plugins")
}

fun deployablePluginProjects(): List<Project> =
    subprojects.filter {
        it.path.startsWith(":plugins:")
            && it.file("src/main/resources/plugin.yml").exists()
            && it.name != "rootrecord-common"
            && it.name !in retiredPluginNames
    }

fun publicPluginDeployDirs(): List<File> = listOf(
    webPluginsDir.asFile,
    webPluginsBuildDir.asFile,
    emergentWebPluginsDir.asFile,
)

fun pluginDeployDirs(): List<File> = listOf(
    rootProject.layout.projectDirectory.dir("out").asFile,
    hostHandoffPluginsDir.asFile,
    workspaceHandoffPluginsDir,
    liveServerPluginsDir,
    serverPluginsDir.asFile,
) + publicPluginDeployDirs()

fun handoffRootMcConfigDirs(): List<File> {
    val workspaceRoot = rootProject.projectDir.parentFile?.parentFile // Plugin Building/Minecraft -> RootMC
    val claims = workspaceRoot?.resolve("Server Handoffs/1. RootMC - Claims/plugins/RootMC")
    val test = workspaceRoot?.resolve("Server Handoffs/3. RootMC - Test Server/plugins/RootMC")
    return listOfNotNull(
        File(workspaceHandoffPluginsDir, "RootMC"),
        claims,
        test,
        File(liveServerPluginsDir, "RootMC"),
        File(hostHandoffPluginsDir.asFile, "RootMC"),
        File(serverPluginsDir.asFile, "RootMC"),
    )
}

fun pruneRetiredAndHaltedArtifacts(logger: org.gradle.api.logging.Logger) {
    val retiredJarPrefixes = buildSet {
        addAll(listOf("blocknotes-", "rootstat-", "plugin-template-"))
        haltedPluginNames.forEach { add("$it-") }
        retiredPluginNames.forEach { add("$it-") }
    }
    for (folder in pluginDeployDirs()) {
        if (!folder.isDirectory) continue
        folder.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jar") }
            ?.forEach { file ->
                if (retiredJarPrefixes.any { file.name.startsWith(it) }) {
                    if (file.delete()) {
                        logger.lifecycle("Pruned retired/halted jar: ${file.name} from ${folder.path}")
                    }
                }
            }
    }
    // Private plugins must never remain downloadable on rootmc.net.
    for (folder in publicPluginDeployDirs()) {
        if (!folder.isDirectory) continue
        folder.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jar") }
            ?.forEach { file ->
                if (privateServerOnlyPluginNames.any { jarBelongsToPlugin(file.name, it) }) {
                    if (file.delete()) {
                        logger.lifecycle("Pruned private jar from public plugins: ${file.name} from ${folder.path}")
                    }
                }
            }
    }
    // Towny hosts skip Root-Claims — drop jar + yml from Towny handoff targets.
    for (folder in pluginDeployDirs()) {
        if (!folder.isDirectory || !isTownyHandoffPluginsDir(folder)) continue
        folder.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jar") && jarBelongsToPlugin(it.name, "root-claims") }
            ?.forEach { file ->
                if (file.delete()) {
                    logger.lifecycle("Pruned Claims-only jar from Towny handoff: ${file.name}")
                }
            }
        val claimsYml = File(folder, "RootMC/root-claims.yml")
        if (claimsYml.isFile && claimsYml.delete()) {
            logger.lifecycle("Pruned Claims-only config from Towny handoff: ${claimsYml.path}")
        }
    }
    for (configDir in handoffRootMcConfigDirs()) {
        if (!configDir.isDirectory) continue
        for (configName in haltedPluginConfigFiles) {
            val file = File(configDir, configName)
            if (file.isFile && file.delete()) {
                logger.lifecycle("Pruned halted config: ${file.path}")
            }
        }
        // Retired plugin ymls — keep live filenames still used by successors
        // (rootmc-shops.yml → root-chestshops; shops.yml = listings store).
        val keepRetiredConfigNames = setOf("rootmc-shops.yml", "shops.yml")
        for (retired in retiredPluginNames) {
            val configName = "$retired.yml"
            if (configName in keepRetiredConfigNames) continue
            val file = File(configDir, configName)
            if (file.isFile && file.delete()) {
                logger.lifecycle("Pruned retired config: ${file.path}")
            }
        }
    }
}

fun jarBelongsToPlugin(jarName: String, pluginName: String): Boolean =
    jarName.startsWith("$pluginName-") && jarName.endsWith(".jar")

fun owningPluginName(jarName: String): String? =
    deployablePluginProjects()
        .map { it.name }
        .sortedByDescending { it.length }
        .firstOrNull { jarBelongsToPlugin(jarName, it) }

fun pruneStalePluginJarsInDir(targetDir: File, pluginName: String, keepJarName: String, logger: org.gradle.api.logging.Logger) {
    if (!targetDir.isDirectory) return
    targetDir.listFiles()
        ?.filter {
            it.isFile
                && owningPluginName(it.name) == pluginName
                && it.name != keepJarName
        }
        ?.forEach {
            if (it.delete()) {
                logger.lifecycle("Pruned stale jar: ${it.name} from ${targetDir.path}")
            }
        }
}

fun pruneAllStalePluginJars(logger: org.gradle.api.logging.Logger) {
    for (proj in deployablePluginProjects()) {
        val keepName = "${proj.name}-${proj.version}.jar"
        val dirs = if (proj.name in privateServerOnlyPluginNames) {
            pluginDeployDirs().filter { it !in publicPluginDeployDirs() }
        } else {
            pluginDeployDirs()
        }
        for (dir in dirs) {
            pruneStalePluginJarsInDir(dir, proj.name, keepName, logger)
        }
    }
}

/** Plugins returned in heartbeat plugin_updates + manifest.json on rootmc.net/plugins */
val heartbeatManifestPluginNames = listOf(
    "root-core",
    "root-ava-core",
    "root-times",
    "root-perms",
    "root-economy",
    "root-chestshops",
    "root-market",
    "root-gamble",
    "root-essentials",
    "root-claims",
    "root-play",
    "root-ops",
    "root-territories",
    "root-iteminfo",
    "root-webstat",
    "root-ping",
    "root-try",
    "root-referrals",
    "root-appreciation",
    "root-memberships",
    "root-haste",
    "root-heads",
    "root-skills",
    "root-bluemap-r2-fix",
    "rootmc",
    "rootmc-official",
)

subprojects {
    apply(plugin = "java")

    group = findProperty("group") as String? ?: "com.rootrecord.minecraft"
    version = findProperty("version") as String? ?: "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        if (serverApiJar.isFile) {
            add("compileOnly", files(serverApiJar))
        } else {
            add("compileOnly", paperApiMaven)
        }
        // Local paper-api jar omits transitive Adventure/BungeeChat â€” needed for compile.
        add("compileOnly", "net.kyori:adventure-api:4.26.1")
        add("compileOnly", "net.kyori:adventure-text-minimessage:4.26.1")
        add("compileOnly", "net.kyori:adventure-text-serializer-legacy:4.26.1")
        add("compileOnly", "net.md-5:bungeecord-chat:1.21-R0.2")
        val isRootPlugin =
            (project.path.startsWith(":plugins:") || project.path.startsWith(":halted:"))
                && project.name != "rootrecord-common"
        if (isRootPlugin) {
            add("implementation", project(":plugins:rootrecord-common"))
        }
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(javaVersion)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaVersion.asInt())
    }

    tasks.named<ProcessResources>("processResources") {
        val pluginVersion = project.version.toString()
        inputs.property("pluginVersion", pluginVersion)
        filesMatching("plugin.yml") {
            expand(mapOf("version" to pluginVersion))
        }
        doFirst {
            val yml = project.file("src/main/resources/plugin.yml")
            if (!yml.exists()) return@doFirst
            yml.readLines().forEachIndexed { idx, line ->
                val t = line.trim()
                if (!t.startsWith("usage:")) return@forEachIndexed
                val rest = t.removePrefix("usage:").trim()
                if (rest.startsWith("\"") || rest.startsWith("'")) return@forEachIndexed
                if (rest.contains(":")) {
                    throw GradleException(
                        "${yml.absolutePath}:${idx + 1} usage: must be quoted — unquoted ':' breaks plugin.yml (Root-Claims /c load failure)",
                    )
                }
            }
        }
    }

    tasks.named<Jar>("jar") {
        archiveClassifier.set("")
        val isRootPlugin =
            (project.path.startsWith(":plugins:") || project.path.startsWith(":halted:"))
                && project.name != "rootrecord-common"
        if (isRootPlugin) {
            dependsOn(":plugins:rootrecord-common:compileJava")
            from(rootProject.project(":plugins:rootrecord-common").extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main").output)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }

    val isDeployablePlugin = project.path.startsWith(":plugins:")
        && project.file("src/main/resources/plugin.yml").exists()
        && project.name != "rootrecord-common"

    if (isDeployablePlugin) {
        val jarTask = tasks.named<Jar>("jar")

        tasks.register<Copy>("copyPluginJar") {
            group = "deployment"
            description = "Copy built jar to Minecraft/out/"
            dependsOn(jarTask)
            from(jarTask)
            into(rootProject.layout.projectDirectory.dir("out"))
            doLast {
                pruneStalePluginJarsInDir(
                    rootProject.layout.projectDirectory.dir("out").asFile,
                    project.name,
                    jarTask.get().archiveFileName.get(),
                    logger,
                )
            }
        }

        tasks.register<Copy>("deployToServer") {
            group = "deployment"
            description = "Copy built jar to server/plugins/ for local Paper testing."
            dependsOn(jarTask)
            from(jarTask)
            into(serverPluginsDir)
            doLast {
                pruneStalePluginJarsInDir(
                    serverPluginsDir.asFile,
                    project.name,
                    jarTask.get().archiveFileName.get(),
                    logger,
                )
            }
        }

        val skipTownyHandoff = project.name in townyHandoffSkipPluginNames

        if (!skipTownyHandoff) {
            tasks.register<Copy>("copyPluginJarToHostHandoff") {
                group = "deployment"
                description = "Copy built jar to server/host-handoff/plugins/ for Shockbyte upload."
                dependsOn(jarTask)
                from(jarTask)
                into(hostHandoffPluginsDir)
                doLast {
                    pruneStalePluginJarsInDir(
                        hostHandoffPluginsDir.asFile,
                        project.name,
                        jarTask.get().archiveFileName.get(),
                        logger,
                    )
                }
            }

            tasks.register<Copy>("copyPluginJarToWorkspaceHandoff") {
                group = "deployment"
                description = "Copy built jar to Server Handoffs/2. RootMC - Towny/plugins/ (workspace server snapshot)."
                dependsOn(jarTask)
                from(jarTask)
                into(workspaceHandoffPluginsDir)
                doFirst { workspaceHandoffPluginsDir.mkdirs() }
                doLast {
                    pruneStalePluginJarsInDir(
                        workspaceHandoffPluginsDir,
                        project.name,
                        jarTask.get().archiveFileName.get(),
                        logger,
                    )
                }
            }

            tasks.register<Copy>("copyPluginJarToLiveServer") {
                group = "deployment"
                description = "Copy built jar to Server Handoffs/2. RootMC - Towny/plugins/ (FileZilla / Shockbyte handoff)."
                dependsOn(jarTask)
                from(jarTask)
                into(liveServerPluginsDir)
                doFirst { liveServerPluginsDir.mkdirs() }
                doLast {
                    pruneStalePluginJarsInDir(
                        liveServerPluginsDir,
                        project.name,
                        jarTask.get().archiveFileName.get(),
                        logger,
                    )
                }
            }
        }

        val privateServerOnly = project.name in privateServerOnlyPluginNames
        if (!privateServerOnly) {
            tasks.register<Copy>("copyPluginJarToWebPublish") {
                group = "deployment"
                description = "Copy built jar to Web Files/rootmc-web/public/plugins/ for rootmc.net."
                dependsOn(jarTask)
                doNotTrackState("web plugins dir may be pruned concurrently by sibling tasks")
                from(jarTask)
                into(webPluginsDir)
                doLast {
                    pruneStalePluginJarsInDir(
                        webPluginsDir.asFile,
                        project.name,
                        jarTask.get().archiveFileName.get(),
                        logger,
                    )
                }
            }
        }

        tasks.named("build") {
            val copyTargets = mutableListOf(
                "copyPluginJar",
                "deployToServer",
            )
            if (!skipTownyHandoff) {
                copyTargets.addAll(
                    listOf(
                        "copyPluginJarToHostHandoff",
                        "copyPluginJarToWorkspaceHandoff",
                        "copyPluginJarToLiveServer",
                    ),
                )
            }
            if (!privateServerOnly) {
                copyTargets.add("copyPluginJarToWebPublish")
            }
            dependsOn(copyTargets)
            finalizedBy(":pruneAllStalePluginJars", ":pruneObsoletePluginJars", ":generatePluginManifest")
        }
    }
}

tasks.register("generatePluginManifest") {
    group = "deployment"
    description = "Write Web Files/rootmc-web/public/plugins/manifest.json from plugin versions."
    dependsOn(
        deployablePluginProjects()
            .filter { it.name !in privateServerOnlyPluginNames }
            .map { "${it.path}:copyPluginJarToWebPublish" },
    )
    doLast {
        val manifest = linkedMapOf<String, Any>()
        for (name in heartbeatManifestPluginNames) {
            if (name in privateServerOnlyPluginNames) continue
            val proj = project(":plugins:$name")
            val version = proj.version.toString()
            val filename = "${proj.name}-$version.jar"
            manifest[name] = mapOf(
                "version" to version,
                "filename" to filename,
                "url" to "$pluginPublishBaseUrl/$filename",
            )
        }
        val manifestFile = webPluginsDir.asFile.resolve("manifest.json")
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + System.lineSeparator())
    }
}

tasks.register("pruneAllStalePluginJars") {
    group = "deployment"
    description = "Remove older Root plugin jars from out/, handoff, server/plugins/, and rootmc-web/public/plugins/."
    doLast { pruneAllStalePluginJars(logger) }
}

tasks.register("pruneObsoletePluginJars") {
    group = "deployment"
    description = "Remove retired + halted plugin jars and halted RootMC yml from all deploy/handoff folders."
    doLast { pruneRetiredAndHaltedArtifacts(logger) }
}

tasks.register("publishPlugins") {
    group = "deployment"
    description = "Build all Paper plugins; copy jars to out/, Server Handoffs/2. RootMC - Towny/plugins/, host-handoff/, server/plugins/, rootmc-web/public/plugins/."
    dependsOn(deployablePluginProjects().map { "${it.path}:build" })
    finalizedBy("pruneAllStalePluginJars", "pruneObsoletePluginJars", "generatePluginManifest")
}

tasks.register("buildAllPlugins") {
    group = "build"
    description = "Build every deployable plugin under plugins/ (excludes halted-development and plugin-template)."
    dependsOn(deployablePluginProjects().map { "${it.path}:build" })
    finalizedBy("pruneObsoletePluginJars")
}

tasks.register("deployAllPlugins") {
    group = "deployment"
    description = "Build and copy every plugin jar to server/plugins/."
    dependsOn(deployablePluginProjects().map { "${it.path}:deployToServer" })
    finalizedBy("pruneObsoletePluginJars")
}
