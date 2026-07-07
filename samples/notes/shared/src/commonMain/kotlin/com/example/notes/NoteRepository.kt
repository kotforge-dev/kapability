package com.example.notes

/**
 * Trivial in-memory store. The point is only to prove both platform entry points reach the *same*
 * commonMain implementation.
 */
class NoteRepository {
    private val notes = mutableListOf<Note>()
    private var seq = 0

    fun create(title: String, content: String, priority: Priority, tags: List<String>, dueLabel: String?): Note {
        val note = Note(
            id = "note-${++seq}",
            title = title,
            content = content,
            priority = priority,
            tags = tags,
            dueLabel = dueLabel,
        )
        notes += note
        return note
    }

    fun latest(): Note = notes.lastOrNull()
        ?: Note(id = "note-0", title = "No notes yet", content = "", priority = Priority.LOW, tags = emptyList(), dueLabel = null)
}
