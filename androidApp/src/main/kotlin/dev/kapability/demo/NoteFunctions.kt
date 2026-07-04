package dev.kapability.demo

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.service.AppFunction
import dev.kapability.KapabilityRuntime

/**
 * The HAND-WRITTEN Android AppFunction wrapper — the exact code the Kapability KSP processor will
 * generate from a commonMain `@Capability` in M1. It does no business logic itself: it decodes its
 * parameters, dispatches through the single [KapabilityRuntime.invoke] entry point (RFC D2), and
 * re-encodes the result. This proves the OS -> @AppFunction -> invoke() -> commonMain chain.
 */
class NoteFunctions {

    /**
     * Creates a note with the given title and content.
     *
     * @param appFunctionContext The context in which the AppFunction is executed.
     * @param title The title of the note.
     * @param content The body content of the note.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun createNote(
        appFunctionContext: AppFunctionContext,
        title: String,
        content: String,
    ): NoteResult {
        val result = KapabilityRuntime.invoke(
            id = "createNote",
            params = mapOf("title" to title, "content" to content),
        )
        return NoteResult(
            id = result.getValue("id"),
            title = result.getValue("title"),
            content = result.getValue("content"),
        )
    }
}
