plugins {
    // Same Ornithe Loom + Ploceus setup as the other OneConfig 1.8.9 mods in D:\Mods.
    id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT"
    id("ploceus") version "1.17-SNAPSHOT"
    id("dev.deftu.gradle.bloom") version "0.2.0"
}

ploceus {
    setIntermediaryGeneration(2)
}

val modId = "dynamicskies"
val modName = "Dynamic Skies"
val modVersion = "1.2.1"
val mcVersion = "1.8.9"
val loaderVersion = "0.19.5"
val oslVersion = "0.21.0"
val oneconfigVersion = "1.2.0"
val javaVersion = 25

version = "$modVersion+$mcVersion"
group = "com.ootruffle"
base.archivesName.set(modId)

repositories {
    mavenCentral()
    google()

    // Hosts the Compose/androidx tree OneConfig's "-ornithe" artifact pulls in.
    maven("https://maven.cloverclient.com/releases")
    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://maven.fabricmc.net/")
    maven("https://repo.polyfrost.org/releases")
    maven("https://repo.polyfrost.org/snapshots")
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(ploceus.mcpMappings("stable", "1.8.9", "22"))

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    ploceus.dependOsl(oslVersion)

    modImplementation("org.polyfrost.oneconfig:$mcVersion-ornithe:$oneconfigVersion")
    for (module in arrayOf("commands", "config", "config-impl", "events", "internal", "ui", "utils")) {
        implementation("org.polyfrost.oneconfig:$module:$oneconfigVersion")
    }
}

// OneConfig's 1.8.9-ornithe build runs the game on LWJGL 3 through its "lenis"
// layer. Vanilla's LWJGL 2 jars on the dev run classpath clash with it
// (VerifyError in lenis' DisplaySdl), so keep them for compiling only.
configurations.runtimeClasspath {
    exclude(group = "org.lwjgl.lwjgl", module = "lwjgl")
    exclude(group = "org.lwjgl.lwjgl", module = "lwjgl_util")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        runDirectory = rootProject.file("run")
    }
    runConfigs.remove(runConfigs["server"])
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

bloom {
    replacement("@MOD_ID@", modId)
    replacement("@MOD_NAME@", modName)
    replacement("@MOD_VERSION@", modVersion)
}

// Keep only this version's jars in build/libs, so an older build can't be loaded by mistake.
tasks.named("assemble") {
    doLast {
        val current = "$modId-$version"
        layout.buildDirectory.dir("libs").get().asFile.listFiles()
            ?.filter { it.name.startsWith("$modId-") && it.name.endsWith(".jar") && !it.name.startsWith(current) }
            ?.forEach {
                it.delete()
                logger.lifecycle("Removed old build ${it.name}")
            }
    }
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_version" to modVersion,
        "loader_version" to loaderVersion,
        "osl_version" to oslVersion,
        "oneconfig_version" to oneconfigVersion,
        "java_version" to javaVersion.toString()
    )
    inputs.properties(props)
    filesMatching(listOf("fabric.mod.json", "mixins.$modId.json")) {
        expand(props)
    }
}
