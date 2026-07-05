package dev.kapability.runtime

/** The encoded inbound parameters for a capability invocation. */
typealias CapabilityParams = Map<String, String>

/** The encoded result of a capability invocation. */
typealias CapabilityResult = Map<String, String>

/**
 * Thrown when a capability id is unknown, or a required parameter is missing / cannot be decoded.
 * The generated `KapabilityRuntime.invoke()` dispatch uses this for a clear, uniform error surface
 * across platforms.
 */
class CapabilityException(message: String) : RuntimeException(message)

/**
 * Reads a required parameter from the encoded `invoke()` payload, throwing a clear
 * [CapabilityException] (rather than a raw NoSuchElementException) when it is absent.
 * Referenced by generated dispatch code so it lives in the published runtime, not per consumer.
 */
fun Map<String, String>.requiredParam(key: String): String =
    this[key] ?: throw CapabilityException("Missing required parameter: $key")
