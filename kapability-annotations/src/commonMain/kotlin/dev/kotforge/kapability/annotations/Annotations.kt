package dev.kotforge.kapability.annotations

/** Platforms a capability can be surfaced to. */
enum class Platform { ANDROID, IOS }

/**
 * Marks a function as an app capability. The Kapability KSP processor generates the platform glue —
 * an Android `@AppFunction` wrapper and (from M2) an iOS Swift `AppIntent` — that dispatch into this
 * function through the single generated `KapabilityRuntime.invoke()` entry point.
 *
 * Declare it once in `commonMain`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Capability(
    val description: String,
    val platforms: Array<Platform> = [Platform.ANDROID, Platform.IOS],
)

/** Describes a capability parameter (surfaced to the on-device agent). */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class CapabilityParam(
    val description: String,
)

/**
 * Marks a data class as a structured capability type. Maps to `@AppFunctionSerializable` on Android
 * and (from M2) a Swift `AppEntity` on iOS.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CapabilityEntity(
    val description: String = "",
)

/** Marks the identity property of a [CapabilityEntity]. */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class CapabilityId
