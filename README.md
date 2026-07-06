# Kapability

**Declare an app capability once in Kotlin Multiplatform `commonMain` — get native Android
[AppFunctions](https://developer.android.com/ai/appfunctions) and iOS
[App Intents](https://developer.apple.com/documentation/appintents) generated for you.**

Android (Gemini) and iOS (Siri / Apple Intelligence) are converging on the same idea: apps expose
structured, self-describing functions that on-device AI agents can discover and invoke. The two
APIs are conceptually identical but completely incompatible in implementation — so a KMP team today
writes and maintains two parallel capability layers.

Kapability is a **Gradle plugin + KSP processor + thin runtime** that lets you write this once:

```kotlin
// commonMain
@CapabilityEntity(description = "A note in the app")
data class Note(
    @CapabilityId val id: String,
    val title: String,
    val content: String,
)

class NoteCapabilities(private val repo: NoteRepository) {

    @Capability(description = "Create a note with the given title and content")
    suspend fun createNote(
        @CapabilityParam(description = "Title of the note") title: String,
        @CapabilityParam(description = "Body content of the note") content: String,
    ): Note = repo.create(title, content)
}
```

…and get, at build time:

- **Android** — a generated `@AppFunction` wrapper + `@AppFunctionSerializable` types feeding the
  official `androidx.appfunctions` compiler (AppSearch-indexable, invokable by Gemini).
- **iOS** — generated Swift `AppIntent` + `AppEntity` whose `perform()` delegates into the shared
  KMP framework (via [SKIE](https://skie.touchlab.co/) for `suspend` → Swift `async`).

Both platforms dispatch through a single generated `KapabilityRuntime.invoke()` entry point.

> **Status: early / pre-release (0.1).** Proven end-to-end on both platforms; APIs may change.

## Setup

Apply the plugin to your KMP module and your Android app module:

```kotlin
// shared/build.gradle.kts (your KMP module)
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("co.touchlab.skie")        // required for the iOS suspend->async bridge
    id("dev.kotforge.kapability")
}
```

```kotlin
// androidApp/build.gradle.kts (your Android application)
plugins {
    id("com.android.application")
    kotlin("android")
    id("dev.kotforge.kapability")
}

kapability {
    capabilityProject.set(project(":shared"))
}
```

The plugin wires the KSP processor, the generated-source directories, task ordering, and the
`androidx.appfunctions` two-pass — nothing else to configure.

## How it works

```
commonMain @Capability
        │  Kapability KSP processor
        ▼
  ┌─────────────────────────────────────────────┐
  │ generated KapabilityRuntime.invoke() (common)│
  └───────────────┬───────────────┬─────────────┘
    Android glue  │               │  iOS glue
   @AppFunction   │               │  Swift AppIntent
  @AppFunctionSerializable        │  AppEntity
        │                         │  (SKIE async bridge)
        ▼                         ▼
  androidx.appfunctions      App Intents runtime
  (Gemini)                   (Siri / Shortcuts / Spotlight)
```

## Modules

| Artifact | Purpose |
|---|---|
| `dev.kotforge:kapability-annotations` | Public API: `@Capability`, `@CapabilityEntity`, `@CapabilityParam`, `@CapabilityId`, `Platform` |
| `dev.kotforge:kapability-runtime` | The `invoke()` bridge types + error model |
| `dev.kotforge:kapability-processor` | KSP2 processor (code generation) |
| `dev.kotforge.kapability` (Gradle plugin) | Wires everything into your build |

## Requirements

- Kotlin **2.2.20**, KSP2, AGP **8.11+**, `compileSdk 36` (Android 16 AppFunctions)
- **SKIE** (applied by you) for the iOS `suspend` → `async` bridge
- Xcode 16+ for the iOS App Intents build

## Supported types (0.1)

`String`, `Int`, `Double`, `Boolean` — as parameters and `@CapabilityEntity` properties, returning a
`@CapabilityEntity`. Anything else **fails the build with a clear error**. `List<T>`, enums, nullable,
and `Date` are on the roadmap (they need a richer bridge codec — the platforms already support them).

`@Capability(platforms = [Platform.ANDROID])` limits a capability to one platform.

## Sample

See [`samples/notes`](samples/notes) — a full KMP app that consumes the published SDK from a
separate Gradle build (proving the external-consumer path) and runs on both an Android 16+ emulator
(`adb shell cmd app_function execute-app-function`) and the iOS simulator (App Intents test).

## Roadmap

- `List<T>` / enum / nullable / `Date` support (bridge-codec upgrade)
- Reduced consumer wiring (generate the appfunctions `Provider`; register once in `commonMain`)
- `manifest.json` + a `verifyKapability` drift-detection task
- Local Swift Package emission for iOS distribution
- v0.2+: schema conformance, entity queries / Spotlight, interaction donations

## Contributing & releases

Kapability uses **[Conventional Commits](https://www.conventionalcommits.org/)** + **release-please** for
automated, semver releases. Your commit / squash-merge titles drive the next version:

| Prefix | Effect | Example |
|---|---|---|
| `feat:` | minor bump (`0.1.0` → `0.2.0`) | `feat: support List<T> parameters` |
| `fix:` | patch bump (`0.1.0` → `0.1.1`) | `fix: correct nullable decoding` |
| `feat!:` or a `BREAKING CHANGE:` footer | major bump (`0.1.0` → `1.0.0`) | `feat!: rename @Capability.description` |
| `docs:` / `chore:` / `refactor:` / `test:` / `ci:` / `build:` | no release; shown in changelog | `docs: expand README` |

On every merge to `main`, release-please maintains a **"chore(main): release …" PR** with the next
version + updated `CHANGELOG.md`. **Merging that Release PR** creates the `vX.Y.Z` tag and publishes to
**Maven Central** + the **Gradle Plugin Portal** automatically. (A manual "Release (manual)" workflow
exists as a fallback.)

## License

[Apache 2.0](LICENSE)
