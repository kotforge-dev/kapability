package dev.kapability.processor

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation

/** Fully-qualified name of the annotation being applied, or null if it can't be resolved. */
internal fun KSAnnotation.fqn(): String? =
    annotationType.resolve().declaration.qualifiedName?.asString()

/** Reads a `String` argument of the annotation [fqn] applied to this element, if present. */
internal fun KSAnnotated.annotationStringArg(fqn: String, argName: String): String? {
    val annotation = annotations.firstOrNull { it.fqn() == fqn } ?: return null
    val arg = annotation.arguments.firstOrNull { it.name?.asString() == argName } ?: return null
    return arg.value as? String
}
