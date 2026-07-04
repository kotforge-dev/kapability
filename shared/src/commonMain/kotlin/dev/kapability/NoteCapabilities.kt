package dev.kapability

/**
 * The developer-authored capability. In the full SDK this carries the `@Capability`/`@CapabilityParam`
 * annotations (RFC §5.5) that the KSP processor reads; for M0 the annotations are omitted and the
 * function is wired by hand on both platforms. The `suspend` modifier is deliberate: it is what the
 * SKIE bridge (RFC D2) turns into a Swift `async` method.
 */
class NoteCapabilities(private val repo: NoteRepository) {

    suspend fun createNote(title: String, content: String): Note =
        repo.create(title, content)
}
