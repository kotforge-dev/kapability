pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kapability"

// The published SDK. The notes sample under samples/notes is a SEPARATE build that
// consumes these from mavenLocal, so it is intentionally NOT included here.
include(":kapability-annotations")
include(":kapability-runtime")
include(":kapability-processor")
include(":kapability-gradle-plugin")
