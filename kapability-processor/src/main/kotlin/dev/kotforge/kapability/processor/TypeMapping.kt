package dev.kotforge.kapability.processor

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName

/**
 * The scalar types Kapability maps across the string-based invoke() bridge in M3. Anything else
 * (Long, List, enum, nullable, custom classes) fails the build with a clear error — no silent
 * mis-generation. Cross-platform-clean by design: each maps to a native Android type, a native
 * Swift `@Parameter`/`AppEntity` type, and a lossless String round-trip.
 */
internal enum class Scalar { STRING, INT, DOUBLE, BOOLEAN }

/** Classifies a Kotlin type, or null if unsupported (including nullable, for now). */
internal fun scalarOf(type: TypeName): Scalar? {
    if (type.isNullable) return null
    return when (type.toString()) {
        "kotlin.String" -> Scalar.STRING
        "kotlin.Int" -> Scalar.INT
        "kotlin.Double" -> Scalar.DOUBLE
        "kotlin.Boolean" -> Scalar.BOOLEAN
        else -> null
    }
}

/** Kotlin expression suffix to decode a `String` into this scalar (used in generated Kotlin). */
internal fun Scalar.kotlinDecodeSuffix(): String = when (this) {
    Scalar.STRING -> ""
    Scalar.INT -> ".toInt()"
    Scalar.DOUBLE -> ".toDouble()"
    Scalar.BOOLEAN -> ".toBooleanStrict()"
}

/** The native KotlinPoet type used in generated Android signatures. */
internal fun Scalar.androidType(): TypeName = when (this) {
    Scalar.STRING -> STRING
    Scalar.INT -> INT
    Scalar.DOUBLE -> DOUBLE
    Scalar.BOOLEAN -> BOOLEAN
}

/** The native Swift type used in generated `@Parameter`/`AppEntity` declarations. */
internal fun Scalar.swiftType(): String = when (this) {
    Scalar.STRING -> "String"
    Scalar.INT -> "Int"
    Scalar.DOUBLE -> "Double"
    Scalar.BOOLEAN -> "Bool"
}

/** Swift expression decoding `map["key"]` into this scalar. */
internal fun Scalar.swiftDecode(key: String): String = when (this) {
    Scalar.STRING -> "map[\"$key\"] ?? \"\""
    Scalar.INT -> "Int(map[\"$key\"] ?? \"\") ?? 0"
    Scalar.DOUBLE -> "Double(map[\"$key\"] ?? \"\") ?? 0"
    Scalar.BOOLEAN -> "map[\"$key\"] == \"true\""
}

/** Swift expression encoding a variable of this scalar into a `String` for the bridge. */
internal fun Scalar.swiftEncode(varName: String): String = when (this) {
    Scalar.STRING -> varName
    else -> "String($varName)"
}
