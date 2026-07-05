package com.example.notes

import dev.kotforge.kapability.annotations.CapabilityEntity
import dev.kotforge.kapability.annotations.CapabilityId

/**
 * The domain entity, declared once in commonMain. `@CapabilityEntity` makes Kapability generate the
 * platform-native structured types (Android `@AppFunctionSerializable`; iOS `AppEntity`).
 * `priority` (Int) exercises non-String scalar mapping across the bridge.
 */
@CapabilityEntity(description = "A note in the app")
data class Note(
    @CapabilityId val id: String,
    val title: String,
    val content: String,
    val priority: Int,
)
