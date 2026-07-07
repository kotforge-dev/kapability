package dev.kotforge.kapability.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo

// Bridge typealiases (both = String, a JSON object document) from kapability-runtime. The generated
// dispatch reads/writes them through the runtime's decodePayload/buildPayload helpers.
private val CAPABILITY_PARAMS = ClassName("dev.kotforge.kapability.runtime", "CapabilityParams")
private val CAPABILITY_RESULT = ClassName("dev.kotforge.kapability.runtime", "CapabilityResult")
private val CAPABILITY_EXCEPTION = ClassName("dev.kotforge.kapability.runtime", "CapabilityException")
private val DECODE_PAYLOAD = MemberName("dev.kotforge.kapability.runtime", "decodePayload")
private val BUILD_PAYLOAD = MemberName("dev.kotforge.kapability.runtime", "buildPayload")
private val KAPABILITY_RUNTIME = ClassName("dev.kotforge.kapability", "KapabilityRuntime")

/** Types Kapability supports today, for the fail-fast error message. */
private const val SUPPORTED_TYPES =
    "String, Int, Double, Boolean, enums, List<String>, and nullable (?) variants"

private val APP_FUNCTION = ClassName("androidx.appfunctions.service", "AppFunction")
private val APP_FUNCTION_CONTEXT = ClassName("androidx.appfunctions", "AppFunctionContext")
private val APP_FUNCTION_SERIALIZABLE = ClassName("androidx.appfunctions", "AppFunctionSerializable")

private const val GENERATED_PACKAGE = "dev.kotforge.kapability.generated"

class KapabilityProcessor(private val env: SymbolProcessorEnvironment) : SymbolProcessor {

    /** The common (metadata) compilation sees all targets; a leaf target sees exactly one. */
    private val isCommon = env.platforms.size > 1

    /**
     * When set (KSP option `kapability.androidOutDir`), the Android @AppFunction wrapper is written
     * as plain source into this directory during the common pass, for the app to compile and run
     * androidx.appfunctions-compiler over. This is the two-pass wiring the Gradle plugin will own.
     */
    private val androidOutDir: String? = env.options["kapability.androidOutDir"]

    /**
     * When set (KSP option `kapability.swiftOutDir`), the iOS Swift AppIntent/AppEntity are written
     * as plain source into this directory during the common pass, for Xcode to compile into the app
     * (Kotlin/Native cannot produce discoverable App Intents — they must be real Swift).
     */
    private val swiftOutDir: String? = env.options["kapability.swiftOutDir"]

    override fun process(resolver: Resolver): List<KSAnnotated> {
        env.logger.warn("Kapability KSP: platforms=${env.platforms.map { it.platformName }} isCommon=$isCommon")

        val functions = resolver.getSymbolsWithAnnotation(Fqn.CAPABILITY)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.validate() }
            .toList()
        if (functions.isEmpty()) return emptyList()

        val capabilities = functions.map { it.toCapabilityModel() }
        val originating = capabilities.mapNotNull { it.originatingFile }

        // Fail-fast: refuse unsupported types instead of silently mis-generating.
        var hasError = false
        capabilities.forEach { cap ->
            cap.params.filter { it.supported == null }.forEach {
                hasError = true
                env.logger.error(
                    "Kapability: type '${it.type}' for parameter '${it.name}' in capability " +
                        "'${cap.functionName}' is not supported yet. Supported: $SUPPORTED_TYPES. " +
                        "(Date, nested @CapabilityEntity, and non-String/object lists are still pending.)"
                )
            }
            cap.entity?.properties?.filter { it.supported == null }?.forEach {
                hasError = true
                env.logger.error(
                    "Kapability: type '${it.type}' for property '${it.name}' in @CapabilityEntity " +
                        "'${cap.entity.className.simpleName}' is not supported yet. Supported: $SUPPORTED_TYPES. " +
                        "(Date, nested @CapabilityEntity, and non-String/object lists are still pending.)"
                )
            }
        }
        if (hasError) return emptyList()

        if (isCommon) {
            generateCommonRuntime(capabilities, originating)
            // Two-pass mode: emit the Android @AppFunction wrapper as PLAIN SOURCE into a directory
            // the app compiles and runs androidx.appfunctions-compiler over in a fresh KSP round.
            // Without this, the appfunctions aggregating registry is finalized before our generated
            // wrapper appears (one round later) and the function is never indexed.
            androidOutDir?.let { dir ->
                buildAndroidFileSpec(capabilities).writeTo(java.io.File(dir))
            }
            // Emit the iOS Swift AppIntent/AppEntity as plain source for Xcode to compile.
            swiftOutDir?.let { dir ->
                SwiftEmitter.emit(capabilities, java.io.File(dir))
            }
        } else if (androidOutDir == null) {
            // Single-pass fallback (no two-pass dir configured): emit via KSP CodeGenerator.
            buildAndroidFileSpec(capabilities)
                .writeTo(env.codeGenerator, aggregating = true, originatingKSFiles = originating)
        }
        return emptyList()
    }

    // ---- commonMain: the single invoke() entry point + install() DI hook ----

    private fun generateCommonRuntime(caps: List<CapabilityModel>, originating: List<KSFile>) {
        val enclosings = caps.map { it.enclosing }.distinct()

        val runtime = TypeSpec.objectBuilder("KapabilityRuntime")
            .addKdoc("Generated by Kapability. Do not edit.\n\nThe single dispatch entry point both platforms call.")

        // one nullable holder + one install parameter per distinct capability-hosting class
        enclosings.forEach { cls ->
            runtime.addProperty(
                PropertySpec.builder(cls.fieldName(), cls.copy(nullable = true), KModifier.PRIVATE)
                    .mutable(true).initializer("null").build()
            )
        }
        runtime.addFunction(
            FunSpec.builder("install").apply {
                enclosings.forEach { cls -> addParameter(cls.fieldName(), cls) }
                enclosings.forEach { cls -> addStatement("this.%N = %N", cls.fieldName(), cls.fieldName()) }
            }.build()
        )

        val body = CodeBlock.builder().beginControlFlow("return when (id)")
        caps.forEach { cap ->
            body.beginControlFlow("%S ->", cap.id)
            body.addStatement(
                "val instance = %N ?: throw %T(%S)",
                cap.enclosing.fieldName(), CAPABILITY_EXCEPTION,
                "${cap.enclosing.simpleName} is not installed. Call KapabilityRuntime.install(...) at startup.",
            )
            if (cap.params.isNotEmpty()) body.addStatement("val payload = %M(params)", DECODE_PAYLOAD)
            val args = cap.params.map {
                CodeBlock.of("%N = %L", it.name, it.supported!!.kotlinDecode("payload", it.name))
            }
            body.addStatement("val result = instance.%N(%L)", cap.functionName, args.joinToCode(", "))
            // Build the result JSON payload (or "{}" when there is no @CapabilityEntity return type).
            body.beginControlFlow("%M", BUILD_PAYLOAD)
            cap.entity?.properties?.forEach { prop ->
                body.addStatement("%L", prop.supported!!.kotlinEncode(prop.name, CodeBlock.of("result.%N", prop.name)))
            }
            body.endControlFlow()
            body.endControlFlow()
        }
        body.addStatement("else -> throw %T(%P)", CAPABILITY_EXCEPTION, "Unknown capability: \$id")
        body.endControlFlow()

        runtime.addFunction(
            FunSpec.builder("invoke")
                .addModifiers(KModifier.SUSPEND)
                .addParameter("id", STRING)
                .addParameter("params", CAPABILITY_PARAMS)
                .returns(CAPABILITY_RESULT)
                .addCode(body.build())
                .build()
        )

        FileSpec.builder("dev.kotforge.kapability", "KapabilityRuntime")
            .addType(runtime.build())
            .build()
            .writeTo(env.codeGenerator, aggregating = true, originatingKSFiles = originating)
    }

    // ---- android: @AppFunctionSerializable entities + @AppFunction wrappers ----

    private fun buildAndroidFileSpec(allCaps: List<CapabilityModel>): FileSpec {
        val file = FileSpec.builder(GENERATED_PACKAGE, "KapabilityAppFunctions")
        val caps = allCaps.filter { "ANDROID" in it.platforms }

        // one @AppFunctionSerializable data class per distinct @CapabilityEntity return type
        caps.mapNotNull { it.entity }.distinctBy { it.className }.forEach { entity ->
            file.addType(
                TypeSpec.classBuilder(entity.serializableName())
                    .addModifiers(KModifier.DATA)
                    .addAnnotation(APP_FUNCTION_SERIALIZABLE)
                    .primaryConstructor(
                        FunSpec.constructorBuilder().apply {
                            entity.properties.forEach { addParameter(it.name, it.supported!!.androidSurface().androidType()) }
                        }.build()
                    )
                    .apply {
                        entity.properties.forEach {
                            addProperty(
                                PropertySpec.builder(it.name, it.supported!!.androidSurface().androidType())
                                    .initializer(it.name).build()
                            )
                        }
                    }
                    .build()
            )
        }

        // one wrapper class per capability-hosting class, with a @AppFunction per capability
        caps.groupBy { it.enclosing }.forEach { (enclosing, group) ->
            val wrapper = TypeSpec.classBuilder(enclosing.wrapperName())
                .addKdoc("Generated by Kapability. Do not edit.")
            group.forEach { cap -> wrapper.addFunction(appFunctionFor(cap)) }
            file.addType(wrapper.build())
        }

        return file.build()
    }

    private fun appFunctionFor(cap: CapabilityModel): FunSpec {
        val returnType = cap.entity?.serializableName()
            ?: error("M1 supports only @CapabilityEntity return types: ${cap.functionName}")

        val fn = FunSpec.builder(cap.functionName)
            .addModifiers(KModifier.SUSPEND)
            .addAnnotation(
                AnnotationSpec.builder(APP_FUNCTION)
                    .addMember("isDescribedByKDoc = true").build()
            )
            .addParameter("appFunctionContext", APP_FUNCTION_CONTEXT)
            .returns(returnType)

        // KDoc is how androidx.appfunctions extracts descriptions for the agent (isDescribedByKDoc).
        fn.addKdoc("%L\n\n", cap.description)
        fn.addKdoc("@param appFunctionContext The context in which the AppFunction is executed.\n")
        cap.params.forEach { fn.addKdoc("@param %L %L\n", it.name, it.description) }

        // The appfunctions surface uses String for enum-typed params (see androidSurface()).
        cap.params.forEach { fn.addParameter(it.name, it.supported!!.androidSurface().androidType()) }

        // Encode the native params into the JSON bridge payload, dispatch, then decode the result JSON
        // back into the @AppFunctionSerializable entity.
        if (cap.params.isEmpty()) {
            fn.addStatement("val result = %T.invoke(%S, %M { })", KAPABILITY_RUNTIME, cap.id, BUILD_PAYLOAD)
        } else {
            fn.beginControlFlow("val params = %M", BUILD_PAYLOAD)
            cap.params.forEach { p ->
                fn.addStatement("%L", p.supported!!.androidSurface().kotlinEncode(p.name, CodeBlock.of("%N", p.name)))
            }
            fn.endControlFlow()
            fn.addStatement("val result = %T.invoke(%S, params)", KAPABILITY_RUNTIME, cap.id)
        }
        fn.addStatement("val payload = %M(result)", DECODE_PAYLOAD)
        val ctorArgs = cap.entity!!.properties.map {
            CodeBlock.of("%N = %L", it.name, it.supported!!.androidSurface().kotlinDecode("payload", it.name))
        }
        fn.addStatement("return %T(%L)", returnType, ctorArgs.joinToCode(", "))
        return fn.build()
    }
}

private fun ClassName.fieldName(): String = simpleName.replaceFirstChar { it.lowercase() }
private fun ClassName.wrapperName(): ClassName = ClassName(GENERATED_PACKAGE, "${simpleName}AppFunctions")
private fun EntityModel.serializableName(): ClassName = ClassName(GENERATED_PACKAGE, className.simpleName)
