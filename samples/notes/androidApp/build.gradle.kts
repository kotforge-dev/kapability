import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// The Kapability-generated Android @AppFunction wrapper source, emitted by the shared module's
// common KSP pass. Compiling it HERE lets androidx.appfunctions-compiler process it as fresh
// round-1 source so it is correctly indexed (the two-pass the Gradle plugin will own).
val sharedGenDir = project(":shared").layout.buildDirectory.dir("generated/kapability/kotlin")

android {
    namespace = "dev.kapability.demo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.kapability.demo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets.getByName("main").kotlin.srcDir(sharedGenDir)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Ensure the wrapper source exists (shared's common KSP pass) before this app's KSP/compile runs.
tasks.matching { it.name.startsWith("ksp") || it.name.startsWith("compile") }
    .configureEach { dependsOn(":shared:kspCommonMainKotlinMetadata") }

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

ksp {
    // Marks this as the leaf/app module so the appfunctions compiler aggregates all @AppFunction
    // declarations and emits the AppSearch index assets (app_functions.xml / app_functions_v2.xml).
    arg("appfunctions:aggregateAppFunctions", "true")
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.appfunctions)
    implementation(libs.appfunctions.service)
    ksp(libs.appfunctions.compiler)
}
