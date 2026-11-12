plugins {
    id("net.fabricmc.fabric-loom-remap")
}

val minecraftVersion = project.name.removePrefix("fabric-")
version = "1.2.1+$minecraftVersion"
group = rootProject.group
base.archivesName.set("dynamicskies")

repositories {
    mavenCentral()
    maven("https://repo.polyfrost.org/releases")
}

dependencies {
    compileOnly("org.polyfrost.oneconfig:config:1.2.22") { isTransitive = false }
    compileOnly("org.polyfrost.oneconfig:config-impl:1.2.22") { isTransitive = false }
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.19.5")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(21))

sourceSets.main {
    java.srcDir(rootProject.file("src/main/java"))
    java.srcDir(rootProject.file("fabric-common/src/main/java"))
    java.include("**/modern/**", "**/sky/SkyType.java", "**/sky/SkyShader.java", "**/sky/ModernSkyRenderer.java", "**/sky/Meteors.java")
}

tasks.processResources {
    from(rootProject.file("src/main/resources")) { include("assets/**") }
    from(rootProject.file("fabric-common/src/main/resources"))
    filesMatching("fabric.mod.json") { expand("version" to project.version, "minecraft" to minecraftVersion) }
}

