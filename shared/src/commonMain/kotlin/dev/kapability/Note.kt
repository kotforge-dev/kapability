package dev.kapability

/**
 * The domain entity. In M1 this becomes a `@CapabilityEntity`; for the M0 spike it is a
 * plain data class. `asMap()` is the M0 codec: the [KapabilityRuntime.invoke] boundary speaks
 * only `Map<String, String>` so the Kotlin<->Swift (SKIE) bridge needs no entity export yet.
 */
data class Note(
    val id: String,
    val title: String,
    val content: String,
) {
    fun asMap(): Map<String, String> = mapOf(
        "id" to id,
        "title" to title,
        "content" to content,
    )
}
