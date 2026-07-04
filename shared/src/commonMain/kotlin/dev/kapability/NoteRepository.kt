package dev.kapability

/**
 * Trivial in-memory store for the spike. Real persistence is out of scope for M0 — the point
 * is only to prove that both platform entry points reach the *same* commonMain implementation.
 */
class NoteRepository {
    private val notes = mutableListOf<Note>()
    private var seq = 0

    fun create(title: String, content: String): Note {
        val note = Note(id = "note-${++seq}", title = title, content = content)
        notes += note
        return note
    }
}
