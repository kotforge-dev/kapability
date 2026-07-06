import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
}

// Coordinates for every published SDK module (group:artifact:version).
allprojects {
    group = "dev.kotforge"
    // Release CI passes -PkapabilityVersion=<tag> (e.g. 0.1.0); defaults to a snapshot otherwise.
    version = (findProperty("kapabilityVersion") as String?) ?: "0.1.0-SNAPSHOT"
}

// ---- Publishing convention for the library modules (Maven Central via built-in maven-publish) ----
// The Gradle plugin module publishes separately via com.gradle.plugin-publish, so it's excluded here.
val libraryPoms = mapOf(
    "kapability-annotations" to ("Kapability Annotations" to
        "Kapability public API: annotations to declare app capabilities once in commonMain."),
    "kapability-runtime" to ("Kapability Runtime" to
        "Kapability runtime: the invoke() bridge types and error model the generated dispatch uses."),
    "kapability-processor" to ("Kapability Processor" to
        "Kapability KSP2 processor: generates the Android AppFunction and iOS App Intent glue."),
)

subprojects {
    val proj = this
    val pom = libraryPoms[name] ?: return@subprojects
    plugins.withId("maven-publish") {
        pluginManager.apply("signing")

        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                // Maven Central requires a javadoc jar per artifact; Kotlin has none, so publish an
                // empty one. Each publication gets its OWN jar (distinct file) so the per-publication
                // signing tasks don't collide.
                val publicationName = name
                artifact(
                    proj.tasks.register("javadocJar${publicationName.replaceFirstChar { it.uppercase() }}", Jar::class.java) {
                        archiveClassifier.set("javadoc")
                        archiveBaseName.set("javadoc-$publicationName")
                    }
                )
                pom {
                    name.set(pom.first)
                    description.set(pom.second)
                    url.set("https://github.com/kotforge-dev/kapability")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("piyusinha")
                            name.set("Piyush Sinha")
                            url.set("https://github.com/Piyusinha")
                        }
                    }
                    scm {
                        url.set("https://github.com/kotforge-dev/kapability")
                        connection.set("scm:git:git://github.com/kotforge-dev/kapability.git")
                        developerConnection.set("scm:git:ssh://git@github.com/kotforge-dev/kapability.git")
                    }
                }
            }
            // Signed artifacts are staged into a local dir; CI bundles + uploads to the Central Portal.
            repositories {
                maven {
                    name = "staging"
                    url = rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
                }
            }
        }

        extensions.configure<SigningExtension> {
            val key = findProperty("signingInMemoryKey") as String?
            val password = findProperty("signingInMemoryKeyPassword") as String?
            // Optional locally (publishToMavenLocal works unsigned); required once a GPG key is present.
            isRequired = key != null
            if (key != null) {
                useInMemoryPgpKeys(key, password)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}
