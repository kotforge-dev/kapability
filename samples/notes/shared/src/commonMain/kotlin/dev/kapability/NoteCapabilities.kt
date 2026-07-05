package dev.kapability

import dev.kapability.annotations.Capability
import dev.kapability.annotations.CapabilityParam

/**
 * The developer-authored capability — declared ONCE in commonMain. The `@Capability` annotation is
 * all the developer writes; the Kapability KSP processor generates the Android `@AppFunction` wrapper,
 * the `@AppFunctionSerializable` result, and the common `KapabilityRuntime.invoke()` dispatch.
 * The `suspend` modifier is what SKIE turns into a Swift `async` method on iOS.
 */
class NoteCapabilities(private val repo: NoteRepository) {

    @Capability(description = "Create a note with the given title and content")
    suspend fun createNote(
        @CapabilityParam(description = "Title of the note") title: String,
        @CapabilityParam(description = "Body content of the note") content: String,
    ): Note = repo.create(title, content)
}
