package dev.kotforge.kapability.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Property

/** Configuration for the `dev.kotforge.kapability` plugin. */
abstract class KapabilityExtension {
    /**
     * Android app modules only: the KMP module that hosts the `@Capability` declarations (whose
     * generated `@AppFunction` wrapper this app compiles). e.g. `project(":shared")`.
     */
    abstract val capabilityProject: Property<Project>
}
