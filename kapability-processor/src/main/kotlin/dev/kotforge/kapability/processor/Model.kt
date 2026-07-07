package dev.kotforge.kapability.processor

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

internal object Fqn {
    const val CAPABILITY = "dev.kotforge.kapability.annotations.Capability"
    const val CAPABILITY_PARAM = "dev.kotforge.kapability.annotations.CapabilityParam"
    const val CAPABILITY_ENTITY = "dev.kotforge.kapability.annotations.CapabilityEntity"
    const val CAPABILITY_ID = "dev.kotforge.kapability.annotations.CapabilityId"
}

/** A capability parameter with its agent-facing description. [supported] is null if the type is unmapped. */
internal data class ParamModel(
    val name: String,
    val type: TypeName,
    val supported: SupportedType?,
    val description: String,
)

/** One property of a `@CapabilityEntity`. [supported] is null if the type is unmapped. */
internal data class PropModel(
    val name: String,
    val type: TypeName,
    val supported: SupportedType?,
)

/** A `@CapabilityEntity` return type, flattened to the properties we encode over the invoke() bridge. */
internal data class EntityModel(
    val className: ClassName,
    val idProperty: String?,
    val properties: List<PropModel>,
)

/** One `@Capability` function, resolved into everything the generators need. */
internal data class CapabilityModel(
    val id: String,                 // the invoke() id = function simple name
    val enclosing: ClassName,       // class hosting the function (instance supplied via install())
    val functionName: String,
    val description: String,
    val params: List<ParamModel>,
    val entity: EntityModel?,       // non-null when the return type is a @CapabilityEntity
    val platforms: Set<String>,     // "ANDROID" / "IOS" — which platforms to generate glue for
    val originatingFile: KSFile?,
)

internal fun KSFunctionDeclaration.toCapabilityModel(): CapabilityModel {
    val enclosingClass = parentDeclaration as? KSClassDeclaration
        ?: error("@Capability must be a member function of a class: $simpleName")

    val description = annotationStringArg(Fqn.CAPABILITY, "description") ?: ""

    // Read @Capability(platforms = [...]); default to both. Enum entries are matched by name so we
    // don't depend on how KSP renders the argument value.
    val platformsRaw = annotations.firstOrNull { it.fqn() == Fqn.CAPABILITY }
        ?.arguments?.firstOrNull { it.name?.asString() == "platforms" }
        ?.value as? List<*>
    val platforms = buildSet {
        val raw = platformsRaw?.joinToString(",") { it.toString() } ?: ""
        if (raw.contains("ANDROID")) add("ANDROID")
        if (raw.contains("IOS")) add("IOS")
    }.ifEmpty { setOf("ANDROID", "IOS") }

    val params = parameters.map { p ->
        ParamModel(
            name = p.name!!.asString(),
            type = p.type.toTypeName(),
            supported = classifyType(p.type.resolve()),
            description = p.annotationStringArg(Fqn.CAPABILITY_PARAM, "description") ?: "",
        )
    }

    val returnDecl = returnType?.resolve()?.declaration as? KSClassDeclaration
    val entity = returnDecl
        ?.takeIf { it.annotations.any { a -> a.fqn() == Fqn.CAPABILITY_ENTITY } }
        ?.let { decl ->
            EntityModel(
                className = decl.toClassName(),
                idProperty = decl.getAllProperties()
                    .firstOrNull { it.annotations.any { a -> a.fqn() == Fqn.CAPABILITY_ID } }
                    ?.simpleName?.asString(),
                properties = decl.getAllProperties()
                    .map { PropModel(it.simpleName.asString(), it.type.toTypeName(), classifyType(it.type.resolve())) }
                    .toList(),
            )
        }

    return CapabilityModel(
        id = simpleName.asString(),
        enclosing = enclosingClass.toClassName(),
        functionName = simpleName.asString(),
        description = description,
        params = params,
        entity = entity,
        platforms = platforms,
        originatingFile = containingFile,
    )
}

private fun scalarOf(fqn: String?): Scalar? = when (fqn) {
    "kotlin.String" -> Scalar.STRING
    "kotlin.Int" -> Scalar.INT
    "kotlin.Double" -> Scalar.DOUBLE
    "kotlin.Boolean" -> Scalar.BOOLEAN
    else -> null
}

/**
 * Maps a resolved Kotlin type to the bridge's [SupportedType], or null if unsupported. Supports
 * scalars, enums, `List<scalar>`, and a nullable wrapper over any of those. Rejects (returns null)
 * nested nullables, `List` of enum/object/nullable, and everything else — the caller fails the build.
 */
internal fun classifyType(type: KSType): SupportedType? {
    if (type.isMarkedNullable) {
        val inner = classifyNonNull(type.makeNotNullable()) ?: return null
        return SupportedType.NullableType(inner)
    }
    return classifyNonNull(type)
}

private fun classifyNonNull(type: KSType): SupportedType? {
    val decl = type.declaration as? KSClassDeclaration ?: return null
    val fqn = decl.qualifiedName?.asString()

    scalarOf(fqn)?.let { return SupportedType.ScalarType(it) }

    if (decl.classKind == ClassKind.ENUM_CLASS) {
        val cases = decl.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.ENUM_ENTRY }
            .map { it.simpleName.asString() }
            .toList()
        if (cases.isEmpty()) return null
        return SupportedType.EnumType(decl.toClassName(), cases)
    }

    if (fqn == "kotlin.collections.List") {
        val arg = type.arguments.firstOrNull()?.type?.resolve() ?: return null
        if (arg.isMarkedNullable) return null
        val elem = scalarOf((arg.declaration as? KSClassDeclaration)?.qualifiedName?.asString()) ?: return null
        // androidx.appfunctions (alpha08) supports only List<String> among list types; numeric lists
        // would need primitive-array mapping on the Android surface — deferred to a follow-up.
        if (elem != Scalar.STRING) return null
        return SupportedType.ListType(elem)
    }

    return null
}
