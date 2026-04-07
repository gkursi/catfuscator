plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":utils"))
    implementation(kotlin("reflect"))
    implementation(libs.bundles.project)
    implementation(libs.bundles.logging)
}

application {
    mainClass = "xyz.qweru.cat.MainKt"
}
