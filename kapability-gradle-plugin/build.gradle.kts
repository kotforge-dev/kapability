import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.plugin.publish)   // Gradle's official plugin-publish (Plugin Portal + maven-publish)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // The consumer applies these; the plugin only reacts to and configures them.
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    website.set("https://github.com/Piyusinha/kapability")
    vcsUrl.set("https://github.com/Piyusinha/kapability")
    plugins {
        create("kapability") {
            id = "dev.kotforge.kapability"
            implementationClass = "dev.kotforge.kapability.gradle.KapabilityPlugin"
            displayName = "Kapability"
            description = "Declare an app capability once in KMP commonMain; generate native Android " +
                "AppFunctions and iOS App Intents."
            tags.set(listOf("kotlin-multiplatform", "kmp", "appfunctions", "appintents", "ksp", "android", "ios"))
        }
    }
}

// Generate a Versions constant so the plugin injects SDK/appfunctions coordinates without the
// consumer specifying versions. SDK version tracks this project's version.
val generateVersions by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/kapability-versions")
    val sdkVersion = project.version.toString()
    val appfunctionsVersion = libs.versions.appfunctions.get()
    val coroutinesVersion = libs.versions.coroutines.get()
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("dev/kotforge/kapability/gradle/Versions.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package dev.kotforge.kapability.gradle

            internal object Versions {
                const val SDK = "$sdkVersion"
                const val APPFUNCTIONS = "$appfunctionsVersion"
                const val COROUTINES = "$coroutinesVersion"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.named("main") { kotlin.srcDir(generateVersions) }
