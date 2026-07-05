package dev.kapability.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

internal object Fqn {
    const val CAPABILITY = "dev.kapability.annotations.Capability"
    const val CAPABILITY_PARAM = "dev.kapability.annotations.CapabilityParam"
    const val CAPABILITY_ENTITY = "dev.kapability.annotations.CapabilityEntity"
    const val CAPABILITY_ID = "dev.kapability.annotations.CapabilityId"
}

/** A capability parameter with its agent-facing description. */
internal data class ParamModel(
    val name: String,
    val type: TypeName,
    val description: String,
)

/** A `@CapabilityEntity` return type, flattened to the properties we encode over the invoke() bridge. */
internal data class EntityModel(
    val className: ClassName,
    val idProperty: String?,
    val properties: List<String>,
)

/** One `@Capability` function, resolved into everything the generators need. */
internal data class CapabilityModel(
    val id: String,                 // the invoke() id = function simple name
    val enclosing: ClassName,       // class hosting the function (instance supplied via install())
    val functionName: String,
    val description: String,
    val params: List<ParamModel>,
    val entity: EntityModel?,       // non-null when the return type is a @CapabilityEntity
    val originatingFile: KSFile?,
)

internal fun KSFunctionDeclaration.toCapabilityModel(): CapabilityModel {
    val enclosingClass = parentDeclaration as? KSClassDeclaration
        ?: error("@Capability must be a member function of a class: $simpleName")

    val description = annotationStringArg(Fqn.CAPABILITY, "description") ?: ""

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
                properties = decl.getAllProperties().map { it.simpleName.asString() }.toList(),
            )
        }

    return CapabilityModel(
        id = simpleName.asString(),
        enclosing = enclosingClass.toClassName(),
        functionName = simpleName.asString(),
        description = description,
        params = params,
        entity = entity,
        originatingFile = containingFile,
    )
}
