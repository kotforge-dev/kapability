package dev.kapability.demo

import androidx.appfunctions.AppFunctionSerializable

/**
 * The `@AppFunctionSerializable` result type the agent receives. In M1 this is what the KSP
 * processor would generate from the commonMain `@CapabilityEntity Note`; here it is hand-written.
 * It is intentionally a *separate* type from commonMain `Note` so the shared module stays free of
 * any `androidx.appfunctions` dependency.
 */
@AppFunctionSerializable
data class NoteResult(
    val id: String,
    val title: String,
    val content: String,
)
