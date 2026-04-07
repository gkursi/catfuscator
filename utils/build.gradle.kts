plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
    id("me.champeau.jmh") version "0.7.3"
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    implementation(libs.bundles.kotlinxEcosystem)
    implementation(libs.bundles.project)
    implementation(libs.bundles.logging)
    testImplementation(kotlin("test"))
}

jmh {
    fork = 2

    resultFormat = "JSON"
    resultsFile = layout.buildDirectory.file("reports/jmh/results.json")
}