package com.example.notes

import dev.kotforge.kapability.annotations.Capability
import dev.kotforge.kapability.annotations.CapabilityParam
import dev.kotforge.kapability.annotations.Platform

/**
 * Developer-authored capabilities, declared ONCE in commonMain. Kapability generates the Android
 * `@AppFunction` glue and the iOS Swift `AppIntent`, both dispatching through the generated
 * `KapabilityRuntime.invoke()`.
 */
class NoteCapabilities(private val repo: NoteRepository) {

    @Capability(description = "Create a note with the given title, content, priority and tags")
    suspend fun createNote(
        @CapabilityParam(description = "Title of the note") title: String,
        @CapabilityParam(description = "Body content of the note") content: String,
        @CapabilityParam(description = "Priority: LOW, MEDIUM or HIGH") priority: Priority,
        @CapabilityParam(description = "Tags used to categorize the note") tags: List<String>,
        @CapabilityParam(description = "Optional human-readable due date label") dueLabel: String?,
    ): Note = repo.create(title, content, priority, tags, dueLabel)

    /** Android-only: exercises `@Capability(platforms = [ANDROID])` — no iOS intent is generated. */
    @Capability(
        description = "Get the most recently created note",
        platforms = [Platform.ANDROID],
    )
    suspend fun latestNote(): Note = repo.latest()
}
