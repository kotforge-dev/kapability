package dev.kotforge.kapability.processor

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName

/** The four primitive scalars Kapability carries across the JSON bridge. */
internal enum class Scalar { STRING, INT, DOUBLE, BOOLEAN }

/**
 * A type Kapability knows how to carry across the JSON invoke() bridge. Anything not classifiable
 * into one of these fails the build (no silent mis-generation). `Nullable` wraps a non-null inner;
 * `List` holds scalar elements only. Enum/nested-object lists and `Date` are not supported yet.
 */
internal sealed interface SupportedType {
    data class ScalarType(val scalar: Scalar) : SupportedType
    data class EnumType(val className: ClassName, val caseNames: List<String>) : SupportedType
    data class ListType(val element: Scalar) : SupportedType
    data class NullableType(val inner: SupportedType) : SupportedType
}

// ---- Android / common Kotlin: native KotlinPoet types ----

private fun Scalar.poet(): TypeName = when (this) {
    Scalar.STRING -> STRING
    Scalar.INT -> INT
    Scalar.DOUBLE -> DOUBLE
    Scalar.BOOLEAN -> BOOLEAN
}

/**
 * The type as `androidx.appfunctions` sees it. That compiler supports neither arbitrary enums nor
 * non-String lists, so an enum is surfaced as its `String` name (the same value carried on the wire;
 * the common dispatch still uses the real enum via `valueOf`). Scalars and `List<String>` pass through.
 */
internal fun SupportedType.androidSurface(): SupportedType = when (this) {
    is SupportedType.EnumType -> SupportedType.ScalarType(Scalar.STRING)
    is SupportedType.NullableType -> SupportedType.NullableType(inner.androidSurface())
    is SupportedType.ScalarType, is SupportedType.ListType -> this
}

/** The native KotlinPoet type used in generated Android signatures & @AppFunctionSerializable props. */
internal fun SupportedType.androidType(): TypeName = when (this) {
    is SupportedType.ScalarType -> scalar.poet()
    is SupportedType.EnumType -> className
    is SupportedType.ListType -> LIST.parameterizedBy(element.poet())
    is SupportedType.NullableType -> inner.androidType().copy(nullable = true)
}

// ---- Kotlin decode: read this type from a runtime `Payload` receiver named [payload] ----

private fun Scalar.reader(): String = when (this) {
    Scalar.STRING -> "string"; Scalar.INT -> "int"; Scalar.DOUBLE -> "double"; Scalar.BOOLEAN -> "bool"
}

private fun Scalar.readerOrNull(): String = "${reader()}OrNull"
private fun Scalar.listReader(): String = "${reader()}List"
private fun Scalar.listReaderOrNull(): String = "${reader()}ListOrNull"

/** Expression decoding this type from `payload` for the given [key]. */
internal fun SupportedType.kotlinDecode(payload: String, key: String): CodeBlock = when (this) {
    is SupportedType.ScalarType -> CodeBlock.of("%N.%N(%S)", payload, scalar.reader(), key)
    is SupportedType.EnumType -> CodeBlock.of("%T.valueOf(%N.enumName(%S))", className, payload, key)
    is SupportedType.ListType -> CodeBlock.of("%N.%N(%S)", payload, element.listReader(), key)
    is SupportedType.NullableType -> when (val i = inner) {
        is SupportedType.ScalarType -> CodeBlock.of("%N.%N(%S)", payload, i.scalar.readerOrNull(), key)
        is SupportedType.EnumType ->
            CodeBlock.of("%N.enumNameOrNull(%S)?.let·{ %T.valueOf(it) }", payload, key, i.className)
        is SupportedType.ListType -> CodeBlock.of("%N.%N(%S)", payload, i.element.listReaderOrNull(), key)
        is SupportedType.NullableType -> error("nested nullable is not representable")
    }
}

// ---- Kotlin encode: statement putting [value] into the enclosing buildPayload { } receiver ----

private fun Scalar.listPut(): String = "put${reader().replaceFirstChar { it.uppercase() }}List"

/** Statement encoding [value] under [key] inside a `buildPayload { }` lambda. */
internal fun SupportedType.kotlinEncode(key: String, value: CodeBlock): CodeBlock = when (this) {
    is SupportedType.ScalarType -> CodeBlock.of("put(%S, %L)", key, value)
    is SupportedType.EnumType -> CodeBlock.of("putEnum(%S, %L.name)", key, value)
    is SupportedType.ListType -> CodeBlock.of("%N(%S, %L)", element.listPut(), key, value)
    is SupportedType.NullableType -> when (val i = inner) {
        is SupportedType.ScalarType -> CodeBlock.of("put(%S, %L)", key, value)
        is SupportedType.EnumType -> CodeBlock.of("putEnum(%S, %L?.name)", key, value)
        is SupportedType.ListType -> CodeBlock.of("%N(%S, %L)", i.element.listPut(), key, value)
        is SupportedType.NullableType -> error("nested nullable is not representable")
    }
}

// ---- Swift: native types + JSONSerialization encode/decode ----

private fun Scalar.swift(): String = when (this) {
    Scalar.STRING -> "String"; Scalar.INT -> "Int"; Scalar.DOUBLE -> "Double"; Scalar.BOOLEAN -> "Bool"
}

private fun Scalar.swiftDefault(): String = when (this) {
    Scalar.STRING -> "\"\""; Scalar.INT -> "0"; Scalar.DOUBLE -> "0"; Scalar.BOOLEAN -> "false"
}

/** The native Swift type used in generated `@Parameter` / `AppEntity` declarations. */
internal fun SupportedType.swiftType(): String = when (this) {
    is SupportedType.ScalarType -> scalar.swift()
    is SupportedType.EnumType -> className.simpleName
    is SupportedType.ListType -> "[${element.swift()}]"
    is SupportedType.NullableType -> "${inner.swiftType()}?"
}

/** Expression producing the JSON dict value for a Swift variable [v] of this type (for encoding params). */
internal fun SupportedType.swiftEncode(v: String): String = when (this) {
    is SupportedType.ScalarType -> v
    is SupportedType.EnumType -> "$v.rawValue"
    is SupportedType.ListType -> v
    is SupportedType.NullableType -> when (val i = inner) {
        is SupportedType.EnumType -> "$v?.rawValue ?? NSNull()"
        else -> "$v ?? NSNull()"
    }
}

/** Expression decoding `dict[key]` into a Swift value of this type (for decoding a result entity). */
internal fun SupportedType.swiftDecode(dict: String, key: String): String = when (this) {
    is SupportedType.ScalarType -> "$dict[\"$key\"] as? ${scalar.swift()} ?? ${scalar.swiftDefault()}"
    is SupportedType.EnumType ->
        "${className.simpleName}(rawValue: $dict[\"$key\"] as? String ?? \"\") ?? .${caseNames.first()}"
    is SupportedType.ListType -> "$dict[\"$key\"] as? [${element.swift()}] ?? []"
    is SupportedType.NullableType -> when (val i = inner) {
        is SupportedType.ScalarType -> "$dict[\"$key\"] as? ${i.scalar.swift()}"
        is SupportedType.EnumType ->
            "($dict[\"$key\"] as? String).flatMap { ${i.className.simpleName}(rawValue: \$0) }"
        is SupportedType.ListType -> "$dict[\"$key\"] as? [${i.element.swift()}]"
        is SupportedType.NullableType -> error("nested nullable is not representable")
    }
}
