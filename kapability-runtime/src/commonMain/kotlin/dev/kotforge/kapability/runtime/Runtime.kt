package dev.kotforge.kapability.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The encoded inbound parameters for a capability invocation: a JSON object document (a `String`).
 * A plain string crosses the Kotlin↔Swift bridge cleanly; each side reads it with its native JSON.
 */
typealias CapabilityParams = String

/** The encoded result of a capability invocation: a JSON object document (a `String`). */
typealias CapabilityResult = String

/**
 * Thrown when a capability id is unknown, or a parameter is missing / cannot be decoded. The
 * generated `KapabilityRuntime.invoke()` dispatch uses this for a clear, uniform error surface
 * across platforms.
 */
class CapabilityException(message: String) : RuntimeException(message)

// ---------------------------------------------------------------------------------------------
// Decode side. Generated dispatch calls `decodePayload(json)` once, then reads typed values by key.
// The kotlinx.serialization types stay behind this wrapper — generated consumer code never sees them.
// ---------------------------------------------------------------------------------------------

/** Parses a capability payload JSON document into a [Payload] reader. */
fun decodePayload(json: String): Payload =
    Payload(
        runCatching { Json.parseToJsonElement(json).jsonObject }
            .getOrElse { throw CapabilityException("Malformed params payload: ${it.message}") }
    )

/** Typed, key-based reader over a decoded payload object. One instance per invocation. */
class Payload internal constructor(private val obj: JsonObject) {

    private fun required(key: String): JsonElement =
        obj[key] ?: throw CapabilityException("Missing required parameter: $key")

    /** Present, non-null element for [key], or null when absent or JSON `null` (for nullable params). */
    private fun optional(key: String): JsonElement? = obj[key]?.takeUnless { it is JsonNull }

    private inline fun <T> decode(key: String, block: () -> T): T =
        runCatching(block).getOrElse { throw CapabilityException("Parameter '$key' has the wrong type: ${it.message}") }

    fun string(key: String): String = decode(key) { required(key).jsonPrimitive.content }
    fun int(key: String): Int = decode(key) { required(key).jsonPrimitive.int }
    fun double(key: String): Double = decode(key) { required(key).jsonPrimitive.double }
    fun bool(key: String): Boolean = decode(key) { required(key).jsonPrimitive.boolean }

    /** Raw enum constant name; generated code passes it to the enum's `valueOf`. */
    fun enumName(key: String): String = decode(key) { required(key).jsonPrimitive.content }

    fun stringOrNull(key: String): String? = optional(key)?.let { decode(key) { it.jsonPrimitive.content } }
    fun intOrNull(key: String): Int? = optional(key)?.let { decode(key) { it.jsonPrimitive.int } }
    fun doubleOrNull(key: String): Double? = optional(key)?.let { decode(key) { it.jsonPrimitive.double } }
    fun boolOrNull(key: String): Boolean? = optional(key)?.let { decode(key) { it.jsonPrimitive.boolean } }
    fun enumNameOrNull(key: String): String? = optional(key)?.let { decode(key) { it.jsonPrimitive.content } }

    // Only String lists are carried today (androidx.appfunctions supports only List<String>);
    // numeric-list helpers will land with the primitive-array mapping follow-up.
    fun stringList(key: String): List<String> = decode(key) { required(key).jsonArray.map { it.jsonPrimitive.content } }
    fun stringListOrNull(key: String): List<String>? = optional(key)?.let { decode(key) { it.jsonArray.map { e -> e.jsonPrimitive.content } } }
}

// ---------------------------------------------------------------------------------------------
// Encode side. Generated dispatch builds a payload with `buildPayload { put(...) }` and returns the
// JSON string. `put` overloads accept nullable scalars (null → JSON null); lists get `put*List`.
// ---------------------------------------------------------------------------------------------

/** Builds a result JSON object document from typed key/value puts. */
fun buildPayload(build: PayloadBuilder.() -> Unit): CapabilityResult =
    PayloadBuilder().apply(build).build()

/** Typed builder for a capability result object. */
class PayloadBuilder internal constructor() {
    private val entries = LinkedHashMap<String, JsonElement>()

    fun put(key: String, value: String?) { entries[key] = JsonPrimitive(value) }
    fun put(key: String, value: Int?) { entries[key] = JsonPrimitive(value) }
    fun put(key: String, value: Double?) { entries[key] = JsonPrimitive(value) }
    fun put(key: String, value: Boolean?) { entries[key] = JsonPrimitive(value) }

    /** Encodes an enum by its constant name (or JSON `null`). */
    fun putEnum(key: String, name: String?) { entries[key] = JsonPrimitive(name) }

    fun putStringList(key: String, value: List<String>?) {
        entries[key] = if (value == null) JsonNull else JsonArray(value.map { JsonPrimitive(it) })
    }

    internal fun build(): CapabilityResult = JsonObject(entries).toString()
}
