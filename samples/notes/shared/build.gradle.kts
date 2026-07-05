import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.skie)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // XCFramework bundles both the device and simulator slices so the iOS app can link one artifact.
    val xcf = XCFramework("Shared")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "dev.kapability.shared")
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain {
            // The Kapability processor generates KapabilityRuntime here (via kspCommonMainMetadata).
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kapability.annotations)
                // api: the generated KapabilityRuntime's public signature exposes runtime typealiases,
                // and the app compiles the generated wrapper against them.
                api(libs.kapability.runtime)
            }
        }
    }
}

// The Android @AppFunction wrapper is emitted here as plain source; the app compiles it and runs
// androidx.appfunctions-compiler over it (two-pass — see the app module). This wiring is what the
// future dev.kapability Gradle plugin will encapsulate.
val kapabilityAndroidGenDir = layout.buildDirectory.dir("generated/kapability/kotlin")

// The iOS AppIntent/AppEntity Swift is emitted into the app's Generated/ folder for Xcode to compile.
val kapabilitySwiftGenDir = rootDir.resolve("iosApp/Generated")

ksp {
    arg("kapability.androidOutDir", kapabilityAndroidGenDir.get().asFile.absolutePath)
    arg("kapability.swiftOutDir", kapabilitySwiftGenDir.absolutePath)
}

dependencies {
    // Run the Kapability processor only in the common pass: it generates KapabilityRuntime into
    // commonMain and writes the Android wrapper source into kapabilityAndroidGenDir.
    add("kspCommonMainMetadata", libs.kapability.processor)
}

// Generate the common dispatch before any compile OR ksp task that reads the generated srcDir.
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }
    .configureEach { dependsOn("kspCommonMainKotlinMetadata") }

// The processor emits the Android wrapper + Swift via File I/O (not KSP CodeGenerator), so declare
// those dirs as outputs of the metadata task — otherwise a `clean` removes them but the task stays
// up-to-date and never regenerates. (The Gradle plugin will model this properly.)
tasks.matching { it.name == "kspCommonMainKotlinMetadata" }.configureEach {
    outputs.dir(kapabilityAndroidGenDir)
    outputs.dir(kapabilitySwiftGenDir)
}

android {
    namespace = "dev.kapability.shared"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
