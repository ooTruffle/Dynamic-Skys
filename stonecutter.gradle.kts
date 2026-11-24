plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.5" /* [SC] DO NOT EDIT */

@Suppress("UNCHECKED_CAST")
val selectedVersions = gradle.extra["dynamicskies.selectedVersions"] as List<String>

// Builds every selected version and copies the jars to build/libs/<mod version>/.
tasks.register("buildAll") {
    group = "build"
    description = "Builds the mod for every selected Minecraft version (see dynamicskies.versions)."
    dependsOn(selectedVersions.map { ":$it:buildAndCollect" })
}
