pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()

        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.kikugie.dev/releases")

        maven("https://maven.fabricmc.net")
        maven("https://maven.ornithemc.net/releases")
        maven("https://maven.ornithemc.net/snapshots")

        maven("https://maven.architectury.dev/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://repo.essential.gg/repository/maven-public")

        maven("https://repo.polyfrost.org/releases")
        maven("https://repo.polyfrost.org/snapshots")

        maven("https://maven.deftu.dev/releases")
        maven("https://maven.deftu.dev/snapshots")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dynamicskies"
