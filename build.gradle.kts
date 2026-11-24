import net.ornithemc.ploceus.api.PloceusGradleExtensionApi

plugins {
    id("dev.kikugie.loom-back-compat")
    id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT" apply false
    id("ploceus") version "1.17-SNAPSHOT" apply false
}

// 1.8.9 runs on Ornithe with MCP names and keeps its own code in src/legacy; every other
// version is Fabric with Mojang names, sharing src/main's `modern` package.
val isLegacy = sc.current.version == "1.8.9"
val ploceus = if (isLegacy) {
    pluginManager.apply("net.fabricmc.fabric-loom-remap")
    pluginManager.apply("ploceus")

    extensions.getByType<PloceusGradleExtensionApi>().apply {
        setIntermediaryGeneration(2)
    }
} else {
    null
}

val modId: String = sc.properties["mod.id"]
val modName: String = sc.properties["mod.name"]
val modVersion: String = sc.properties["mod.version"]
val baseGroup: String = sc.properties["mod.group"]
val mcVersion: String = sc.current.version
val mcCompat: String = sc.properties["mod.mc_compat"]
val loaderVersion: String = sc.properties["deps.fabric_loader"]
val oneconfigVersion: String = sc.properties["deps.oneconfig"]

val requiredJava: JavaVersion = when {
    isLegacy -> JavaVersion.VERSION_25
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

group = baseGroup
version = "$modVersion+$mcVersion"
base.archivesName = modId

repositories {
    mavenCentral()
    google()
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
    // OneConfig's Compose/androidx dependency tree (lenis, etc.) lives here.
    maven("https://maven.cloverclient.com/releases")
    maven("https://repo.papermc.io/repository/maven-public/")
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net/") }
        filter { includeGroup("net.fabricmc") }
    }
}

sourceSets.main {
    val platform = if (isLegacy) "legacy" else "modern"
    java.srcDir(rootProject.file("src/$platform/java"))
    resources.srcDir(rootProject.file("src/$platform/resources"))
    if (isLegacy) {
        java.exclude("com/ootruffle/dynamicskies/modern/**", "com/ootruffle/dynamicskies/sky/ModernSkyRenderer.java")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    if (isLegacy) {
        mappings(ploceus!!.mcpMappings("stable", mcVersion, sc.properties["deps.mcp_mappings"]))
        ploceus.dependOsl(sc.properties["deps.osl"])
    } else {
        loomx.applyMojangMappings()
    }

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")

    if (isLegacy) {
        modImplementation("org.polyfrost.oneconfig:$mcVersion-ornithe:$oneconfigVersion")
        for (module in arrayOf("commands", "config", "config-impl", "events", "internal", "ui", "utils")) {
            implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
        }
    } else {
        // OneConfig is a required mod at runtime; compile against it without pulling its whole tree.
        modCompileOnly("org.polyfrost.oneconfig:$mcVersion-fabric:$oneconfigVersion") { isTransitive = false }
        for (module in arrayOf("config", "config-impl", "poly-compose")) {
            compileOnly("org.polyfrost.oneconfig:$module:$oneconfigVersion") { isTransitive = false }
        }
        modCompileOnly(fabricApi.module("fabric-command-api-v2", sc.properties["deps.fabric_api"]))
    }
}

if (isLegacy) {
    // OneConfig's 1.8.9-ornithe build runs the game on LWJGL 3 through its "lenis"
    // layer. Vanilla's LWJGL 2 jars on the dev run classpath clash with it
    // (VerifyError in lenis' DisplaySdl), so keep them for compiling only.
    configurations.runtimeClasspath {
        exclude(group = "org.lwjgl.lwjgl", module = "lwjgl")
        exclude(group = "org.lwjgl.lwjgl", module = "lwjgl_util")
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/${if (isLegacy) "legacy" else "modern"}/resources/fabric.mod.json")

    runConfigs.all {
        runDirectory = rootProject.file("run")
    }
    runConfigs.remove(runConfigs["server"])
}

tasks {
    processResources {
        val props = mapOf(
            "mod_id" to modId,
            "mod_name" to modName,
            "mod_version" to modVersion,
            "version" to version.toString(),
            "minecraft" to mcCompat,
            "loader_version" to loaderVersion,
            "osl_version" to (sc.properties.getOrNull<String>("deps.osl") ?: ""),
            "oneconfig_version" to oneconfigVersion,
            "java_version" to requiredJava.majorVersion
        )

        inputs.properties(props)

        filesMatching(listOf("fabric.mod.json", "mixins.$modId.json", "$modId-modern.mixins.json")) {
            expand(props)
        }
    }

    withType<JavaCompile>().configureEach {
        options.release = requiredJava.majorVersion.toInt()
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to the root build/libs/<mod version>/."

        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}
