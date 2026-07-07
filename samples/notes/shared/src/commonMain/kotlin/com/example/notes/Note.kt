package com.example.notes

import dev.kotforge.kapability.annotations.CapabilityEntity
import dev.kotforge.kapability.annotations.CapabilityId

/** Priority level — exercises enum mapping across the bridge (Android enum / iOS AppEnum). */
enum class Priority { LOW, MEDIUM, HIGH }

/**
 * The domain entity, declared once in commonMain. `@CapabilityEntity` makes Kapability generate the
 * platform-native structured types (Android `@AppFunctionSerializable`; iOS `AppEntity`).
 *
 * The properties deliberately cover the richer bridge types: [priority] (enum), [tags]
 * (`List<String>`) and [dueLabel] (nullable) — all now carried over the JSON invoke() bridge.
 */
@CapabilityEntity(description = "A note in the app")
data class Note(
    @CapabilityId val id: String,
    val title: String,
    val content: String,
    val priority: Priority,
    val tags: List<String>,
    val dueLabel: String?,
)
