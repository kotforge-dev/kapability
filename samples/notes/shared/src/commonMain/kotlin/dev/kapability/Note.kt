package dev.kapability

import dev.kapability.annotations.CapabilityEntity
import dev.kapability.annotations.CapabilityId

/**
 * The domain entity, declared once in commonMain. `@CapabilityEntity` makes Kapability generate the
 * platform-native structured types (Android `@AppFunctionSerializable`; iOS `AppEntity` from M2).
 */
@CapabilityEntity(description = "A note in the app")
data class Note(
    @CapabilityId val id: String,
    val title: String,
    val content: String,
)
