package dev.kapability

/**
 * The single `invoke()` entry point mandated by RFC Decision D2.
 *
 * Both platform paths — the Android `@AppFunction` wrapper and the iOS Swift `AppIntent` — dispatch
 * through here, never touching the repository directly. This is what M0's exit criterion validates,
 * and keeping the whole Kotlin<->Swift surface to this one `suspend fun` is what makes the SKIE
 * bridge swappable for Swift Export later (RFC O2) without changing any generated intent.
 *
 * Exposed as an `object` so SKIE surfaces it to Swift as `KapabilityRuntime.shared`.
 */
object KapabilityRuntime {
    private val capabilities = NoteCapabilities(NoteRepository())

    suspend fun invoke(id: String, params: Map<String, String>): Map<String, String> = when (id) {
        "createNote" -> capabilities.createNote(
            title = params.getValue("title"),
            content = params.getValue("content"),
        ).asMap()
        else -> error("Unknown capability: $id")
    }
}
