<div align="center">

# ⚡ Kapability

### Declare an app capability **once** in Kotlin Multiplatform — ship it to **Android AppFunctions** _and_ **iOS App Intents**.

[![Maven Central](https://img.shields.io/maven-central/v/dev.kotforge/kapability-annotations?style=for-the-badge&logo=apachemaven&logoColor=white&label=Maven%20Central&color=3DDC84)](https://central.sonatype.com/artifact/dev.kotforge/kapability-annotations)
[![Gradle Plugin](https://img.shields.io/gradle-plugin-portal/v/dev.kotforge.kapability?style=for-the-badge&logo=gradle&logoColor=white&label=Gradle%20Plugin&color=02303A)](https://plugins.gradle.org/plugin/dev.kotforge.kapability)
[![License](https://img.shields.io/badge/License-Apache%202.0-D22128?style=for-the-badge&logo=apache&logoColor=white)](LICENSE)

[![CI](https://img.shields.io/github/actions/workflow/status/kotforge-dev/kapability/ci.yml?branch=main&style=flat-square&logo=githubactions&logoColor=white&label=CI)](https://github.com/kotforge-dev/kapability/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS-4285F4?style=flat-square)](#requirements)
[![KMP](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)

</div>

---

Android (Gemini) and iOS (Siri / Apple Intelligence) are converging on the same idea: apps expose
structured, self-describing functions that on-device AI agents can discover and invoke. Android calls
it **[AppFunctions](https://developer.android.com/ai/appfunctions)**; Apple calls it
**[App Intents](https://developer.apple.com/documentation/appintents)**. The two APIs are conceptually
identical — annotated function + typed params + description + entity types — but completely incompatible
in implementation. So a KMP team today writes and maintains **two parallel capability layers**.

**Kapability collapses that into one.** A Gradle plugin + KSP processor + thin runtime that turns a single
`@Capability` declaration in `commonMain` into native glue for both platforms at build time.

> [!NOTE]
> **Status: `0.1.0` — early / pre-release.** Proven end-to-end on both platforms; the public API may still change.

## ✨ Why Kapability

- 🎯 **Declare once, ship both** — one annotated function in `commonMain`, native on Android and iOS.
- 🔌 **Zero hand-written glue** — the `@AppFunction` wrapper, `@AppFunctionSerializable` types, the Swift `AppIntent` + `AppEntity`, and the dispatch bridge are all generated.
- 🏗️ **Official toolchains** — feeds Google's `androidx.appfunctions` compiler and Xcode's App Intents extraction; no reimplemented metadata formats.
- 🧭 **Graceful asymmetry** — `@Capability(platforms = [ANDROID])` for platform-specific capabilities.
- 🛡️ **Fail fast** — unsupported types stop the build with a clear message instead of mis-generating.
- ⚙️ **One line of setup** — `plugins { id("dev.kotforge.kapability") }`.

## 🚀 Installation

Artifacts are on **Maven Central** and the plugin is on the **Gradle Plugin Portal**, so the default
repositories are enough:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

**Your KMP module** (where capabilities live):

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("co.touchlab.skie")                       // required: Kotlin suspend -> Swift async bridge
    id("dev.kotforge.kapability") version "0.1.0"
}
```

**Your Android app module:**

```kotlin
// androidApp/build.gradle.kts
plugins {
    id("com.android.application")
    kotlin("android")
    id("dev.kotforge.kapability") version "0.1.0"
}

kapability {
    capabilityProject.set(project(":shared"))
}
```

That's it — the plugin adds the `kapability-annotations` / `kapability-runtime` dependencies, wires the
KSP processor + the `androidx.appfunctions` two-pass, and manages the generated sources for you.

## ✍️ Usage

Write this **once**, in `commonMain`:

```kotlin
import dev.kotforge.kapability.annotations.*

@CapabilityEntity(description = "A note in the app")
data class Note(
    @CapabilityId val id: String,
    val title: String,
    val content: String,
    val priority: Int,
)

class NoteCapabilities(private val repo: NoteRepository) {

    @Capability(description = "Create a note with the given title, content and priority")
    suspend fun createNote(
        @CapabilityParam(description = "Title of the note") title: String,
        @CapabilityParam(description = "Body content of the note") content: String,
        @CapabilityParam(description = "Priority from 1 (low) to 5 (high)") priority: Int,
    ): Note = repo.create(title, content, priority)
}
```

…and Kapability generates, at build time:

| Platform | Generated | Reaches |
|---|---|---|
| 🤖 **Android** | `@AppFunction` wrapper + `@AppFunctionSerializable` types → `androidx.appfunctions` | **Gemini** (AppSearch-indexed) |
| 🍎 **iOS** | Swift `struct CreateNoteIntent: AppIntent` + `AppEntity` (via SKIE `async`) | **Siri / Shortcuts / Spotlight** |

Both dispatch through one generated `KapabilityRuntime.invoke()` entry point into your shared code. Wire
your capability instance once at startup (`KapabilityRuntime.install(NoteCapabilities(repo))`), and you're done.

## 🧩 How it works

```
        commonMain: @Capability
                 │  Kapability KSP processor
                 ▼
   ┌──────────────────────────────────────────────┐
   │  generated KapabilityRuntime.invoke() (common) │
   └───────────────┬────────────────┬──────────────┘
      Android glue  │                │  iOS glue
    @AppFunction    │                │  Swift AppIntent
  @AppFunctionSerializable           │  AppEntity  (SKIE async)
                 ▼                    ▼
     androidx.appfunctions       App Intents runtime
        (Gemini)                 (Siri · Shortcuts · Spotlight)
```

## 📦 Modules

| Artifact | Purpose |
|---|---|
| `dev.kotforge:kapability-annotations` | Public API — `@Capability`, `@CapabilityEntity`, `@CapabilityParam`, `@CapabilityId`, `Platform` |
| `dev.kotforge:kapability-runtime` | The `invoke()` bridge types + error model |
| `dev.kotforge:kapability-processor` | KSP2 processor (code generation) |
| `dev.kotforge.kapability` _(Gradle plugin)_ | Wires everything into your build |

## ✅ Supported types

As parameters and `@CapabilityEntity` properties (returning a `@CapabilityEntity`):

- `String`, `Int`, `Double`, `Boolean`
- **enums** — a Swift `AppEnum` on iOS; surfaced as its `String` name to `androidx.appfunctions` on Android
- **`List<String>`**
- **nullable (`?`)** variants of the above

Values travel across a JSON bridge; anything unsupported **fails the build with a clear error**.
Still on the [roadmap](#-roadmap): `Date`, non-`String` lists (`List<Int>` …), nested
`@CapabilityEntity` types, and `List<CustomObject>`.

## 🔧 Requirements

| | Version |
|---|---|
| Kotlin | **2.2.20** (KSP2) |
| Android Gradle Plugin | **8.11+**, `compileSdk 36` (Android 16 AppFunctions) |
| SKIE | latest (you apply it — for the iOS `suspend` → `async` bridge) |
| Xcode | 16+ (iOS App Intents build) |

## 🧪 Sample

[`samples/notes`](samples/notes) is a full KMP app that consumes the published SDK from a **separate
Gradle build** (proving the external-consumer path) and runs on both an Android 16+ emulator
(`adb shell cmd app_function execute-app-function`) and the iOS simulator (App Intents test).

## 🗺️ Roadmap

- Richer types: `Date`, non-`String` lists (via primitive-array mapping), nested `@CapabilityEntity` + `List<CustomObject>`
- Reduced consumer wiring (generate the appfunctions `Provider`; register once in `commonMain`)
- `manifest.json` + a `verifyKapability` drift-detection task
- Local Swift Package emission for iOS distribution
- v0.2+: schema conformance, entity queries / Spotlight, interaction donations

## 🤝 Contributing & releases

Kapability uses **[Conventional Commits](https://www.conventionalcommits.org/)** + **release-please**.
Commit / squash-merge titles drive the next version:

| Prefix | Bump | Example |
|---|---|---|
| `feat:` | minor (`0.1.0` → `0.2.0`) | `feat: support List<T> parameters` |
| `fix:` | patch (`0.1.0` → `0.1.1`) | `fix: correct nullable decoding` |
| `feat!:` / `BREAKING CHANGE:` | major (→ `1.0.0`) | `feat!: rename @Capability.description` |
| `docs:` `chore:` `refactor:` `test:` `ci:` `build:` | none (changelog only) | `docs: expand README` |

On merge to `main`, release-please maintains a **release PR** (version + `CHANGELOG.md`). Merging it
tags and publishes to Maven Central + the Gradle Plugin Portal automatically.

## 📄 License

```
Copyright 2026 Piyush Sinha

Licensed under the Apache License, Version 2.0 — see LICENSE.
```
