import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        // implementation (not api): the JSON codec is an internal detail of the bridge — generated
        // consumer code talks to the Params/ResultBuilder wrappers below, never to serialization types.
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "dev.kotforge.kapability.runtime"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

