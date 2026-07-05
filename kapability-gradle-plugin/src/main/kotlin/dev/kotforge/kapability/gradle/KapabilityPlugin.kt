package dev.kotforge.kapability.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val GROUP = "dev.kotforge"

/**
 * One context-adaptive plugin:
 *  - applied to a Kotlin Multiplatform module → wires the Kapability processor (generates the common
 *    dispatch + the Android wrapper source + the iOS Swift);
 *  - applied to an Android application module → wires the appfunctions "two-pass" (compiles the
 *    generated wrapper as fresh source and runs androidx.appfunctions-compiler over it).
 *
 * It does NOT apply SKIE or configure KMP targets — those stay consumer-owned.
 */
class KapabilityPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val ext = project.extensions.create("kapability", KapabilityExtension::class.java)
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") { configureLibrary(project) }
        project.pluginManager.withPlugin("com.android.application") { configureAndroidApp(project, ext) }
    }

    private fun configureLibrary(project: Project) {
        project.pluginManager.apply("com.google.devtools.ksp")

        val androidOutDir = project.layout.buildDirectory.dir("generated/kapability/kotlin")
        val swiftOutDir = project.rootProject.layout.projectDirectory.dir("iosApp/Generated")

        // Generated common dispatch lands in the metadata KSP output; make it a commonMain source.
        val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kmp.sourceSets.getByName("commonMain").kotlin.srcDir(
            project.layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin")
        )

        project.dependencies.add("commonMainImplementation", "$GROUP:kapability-annotations:${Versions.SDK}")
        // api: the generated KapabilityRuntime's public signature exposes runtime typealiases.
        project.dependencies.add("commonMainApi", "$GROUP:kapability-runtime:${Versions.SDK}")
        project.dependencies.add("kspCommonMainMetadata", "$GROUP:kapability-processor:${Versions.SDK}")

        val ksp = project.extensions.getByType(KspExtension::class.java)
        ksp.arg("kapability.androidOutDir", androidOutDir.get().asFile.absolutePath)
        ksp.arg("kapability.swiftOutDir", swiftOutDir.asFile.absolutePath)

        // Generate the common dispatch before any compile/ksp task that reads the generated srcDir.
        project.tasks.configureEach { task ->
            if ((task.name.startsWith("compile") || task.name.startsWith("ksp")) &&
                task.name != "kspCommonMainKotlinMetadata"
            ) {
                task.dependsOn("kspCommonMainKotlinMetadata")
            }
        }
        // The processor writes the wrapper + Swift via File I/O; declare them as task outputs so a
        // `clean` regenerates them instead of leaving the task up-to-date.
        project.tasks.matching { it.name == "kspCommonMainKotlinMetadata" }.configureEach { task ->
            task.outputs.dir(androidOutDir)
            task.outputs.dir(swiftOutDir)
        }
    }

    private fun configureAndroidApp(project: Project, ext: KapabilityExtension) {
        project.pluginManager.apply("com.google.devtools.ksp")

        project.dependencies.add("implementation", "androidx.appfunctions:appfunctions:${Versions.APPFUNCTIONS}")
        project.dependencies.add("implementation", "androidx.appfunctions:appfunctions-service:${Versions.APPFUNCTIONS}")
        project.dependencies.add("ksp", "androidx.appfunctions:appfunctions-compiler:${Versions.APPFUNCTIONS}")

        val ksp = project.extensions.getByType(KspExtension::class.java)
        // Leaf/app module aggregates all @AppFunctions into the AppSearch index.
        ksp.arg("appfunctions:aggregateAppFunctions", "true")

        // Register the generated-wrapper srcDir and cross-project ordering at configuration time,
        // but read `capabilityProject` (set later in the consumer's kapability{} block) lazily —
        // adding sources in afterEvaluate is too late for AGP's source-set finalization.
        fun capProject(): Project = ext.capabilityProject.orNull
            ?: error("dev.kotforge.kapability: set `kapability { capabilityProject = project(\":shared\") }` in the app module.")

        val genDir = project.provider {
            capProject().layout.buildDirectory.dir("generated/kapability/kotlin").get().asFile
        }
        val android = project.extensions.getByType(ApplicationExtension::class.java)
        android.sourceSets.getByName("main").kotlin.srcDir(genDir)

        project.tasks.configureEach { task ->
            if (task.name.startsWith("compile") || task.name.startsWith("ksp")) {
                task.dependsOn(project.provider { "${capProject().path}:kspCommonMainKotlinMetadata" })
            }
        }
    }
}
