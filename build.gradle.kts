plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
}

// Coordinates for every published SDK module (group:artifact:version).
allprojects {
    group = "dev.kotforge"
    version = "0.1.0-SNAPSHOT"
}
