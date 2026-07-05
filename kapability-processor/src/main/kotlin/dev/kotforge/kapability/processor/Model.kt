package dev.kotforge.kapability.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
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

/** A capability parameter with its agent-facing description. */
internal data class ParamModel(
    val name: String,
    val type: TypeName,
    val description: String,
)

/** One property of a `@CapabilityEntity`. */
internal data class PropModel(
    val name: String,
    val type: TypeName,
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
                    .map { PropModel(it.simpleName.asString(), it.type.toTypeName()) }.toList(),
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
