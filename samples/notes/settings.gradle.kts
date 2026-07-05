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
        mavenLocal()   // resolve the Kapability SDK (dev.kapability:*:0.1.0-SNAPSHOT) published locally
        google()
        mavenCentral()
    }
}

// Standalone build: it consumes the Kapability SDK purely by Maven coordinate, exactly as an
// external developer's project would — no project(...) references to the SDK modules.
rootProject.name = "notes-sample"

include(":shared")
include(":androidApp")
