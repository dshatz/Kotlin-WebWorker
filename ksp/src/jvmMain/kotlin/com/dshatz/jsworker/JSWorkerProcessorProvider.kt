package com.dshatz.jsworker

import com.dshatz.jsworker.Types.Members.send
import com.dshatz.jsworker.model.ParamInfo
import com.dshatz.jsworker.model.WorkerDesc
import com.dshatz.jsworker.model.WorkerFun
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class JSWorkerProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return JSWorkerProcessor(environment)
    }

    class JSWorkerProcessor(
        private val env: SymbolProcessorEnvironment,
    ) : SymbolProcessor {

        private val codeGenerator: CodeGenerator
            get() = env.codeGenerator

        override fun process(resolver: Resolver): List<KSAnnotated> {
            val workerFilename = env.options["_jsworker_filename"] ?: error("No worker registered. Call asWebWorker() inside kotlin js {} block")

            val workerClasses = resolver.getSymbolsWithAnnotation(Types.Annotations.JsWorker.canonicalName)
                .filterIsInstance<KSClassDeclaration>()
            val workers = collectWorkers(workerFilename, workerClasses)

            val deps = Dependencies(true, sources = workerClasses.mapNotNull { it.containingFile }.toList().toTypedArray())

            workers.flatMap {
                listOf(
                    generateRequests(it),
                    generateResponses(it),
                    generateJsReceiverCode(it),
                    generateProxy(it)
                )
            }.forEach {
                it.writeTo(codeGenerator, deps)
            }

            workers.map {
                it to generateJSEntrypoint(it)
            }.forEach { (worker, script) ->
                codeGenerator.createNewFileByPath(
                    deps,
                    worker.entrypointFileName,
                    extensionName = "js"
                ).use {
                    it.write(script.encodeToByteArray())
                }
            }

            return emptyList()
        }

        private fun generateJSEntrypoint(worker: WorkerDesc): String {
            val depPromises = worker.dependencyModules.associateWith {
                it.replace(".", "").replace("/", "")
            }
            return """
                // Entrypoint for ${worker.cls.canonicalName}.
                if (typeof document === 'undefined') {
                    globalThis.document = {
                        baseURI: self.location.href
                    };
                }
                ${depPromises.entries.joinToString("\n") { (module, promise) ->
                    """
                        let $promise = new Promise((resolve, reject) => {
                            try {
                                self.Module = self.Module || {};
                                self.Module.onRuntimeInitialized = () => {
                                  resolve(self.Module);
                                };
                                self.importScripts("./$module")
                            } catch (e) {
                                reject(e);
                            }
                        })
                    """.trimIndent()
                }}
                Promise.all([${depPromises.values.joinToString()}]).then(() => {
                    console.log("All dependency modules resolved")
                    import("./${worker.workerFileName}")
                }).catch(err => {
                     console.error("Failed to load worker dependencies:", err);
                });
            """.trimIndent()
        }

        private fun generateProxy(worker: WorkerDesc): FileSpec {
            val proxyCls = worker.proxyClass
            val f = FileSpec.builder(proxyCls)
            val type = TypeSpec.classBuilder(proxyCls)
                .addProperty(
                    PropertySpec.builder("worker", Types.CompletableDeferred.parameterizedBy(Types.Worker))
                        .initializer("%M(%S)", Types.Members.createWorkerFromModule, "workers/${worker.entrypointFileName}.js")
                        .build()
                )
                .addProperty(
                    PropertySpec.builder("readyWorker", Types.CompletableDeferred.parameterizedBy(Types.Worker))
                        .initializer("%T()", Types.CompletableDeferred)
                        .build()
                )

            val funSpecs = worker.functions.map { f ->
                val spec = f.funBuilder()
                    .addModifiers(KModifier.SUSPEND)

                val createReq = if (f.parameters.isEmpty()) {
                    CodeBlock.of("%T", f.reqClassName)
                } else {
                    CodeBlock.of("%T(%L)", f.reqClassName, f.parameters.joinToCode {
                        CodeBlock.of("%N", it.name)
                    })
                }
                spec.returns(f.returnType)
//                    .addStatement("val transferable = listOf<%T>(%L).mapNotNull { %M(it) }", Types.JsAny, paramList, Types.Members.transferableOrNull)
                    .addStatement("val result: %T = readyWorker.await().%M<%T, %T, %T>(%L, %L)",
                        f.returnType,
                        send,
                        worker.cls.reqSealedClass(),
                        f.respClassName,
                        f.returnType,
                        createReq,
                        f.paramTransferArray()
                    )
                    .addStatement(
                        "return result",
                        f.returnType
                    )
                spec.build()
            }

            val constructorSpecs = worker.constructors.map { c ->
                val spec = c.constructorBuilder()
                val createReq = if (c.parameters.isEmpty()) {
                    CodeBlock.of("%T", c.reqClassName)
                } else {
                    CodeBlock.of("%T(%L)", c.reqClassName, c.parameters.joinToCode {
                        CodeBlock.of("%N", it.name)
                    })
                }
                spec.addCode(
                    CodeBlock.builder()
                        .beginControlFlow("worker.invokeOnCompletion")
                        .beginControlFlow("if (it == null)")
                        .addStatement("val w = worker.getCompleted()")
                        .addStatement(
                            "w.%M<%T, %T, %T>(%L)",
                            Types.Members.sendIgnoreResult,
                            worker.cls.reqSealedClass(),
                            c.respClassName,
                            c.returnType,
                            createReq
                        )
                        .addStatement("readyWorker.complete(w)")
                        .endControlFlow() // if
                        .endControlFlow() // invokeOnCompletion
                        .build()
                )
                spec.build()
            }

            type.addFunctions(funSpecs)
            type.addFunctions(constructorSpecs)
            f.addType(type.build())
            f.addAnnotation(Types.Annotations.optIn(Types.Annotations.ExperimentalWasmInterop))
            return f.build()
        }

        private fun generateJsReceiverCode(worker: WorkerDesc): FileSpec {
            val cls = worker.cls
            val receiverCls = cls.withSuffix("_Receiver")
            val f = FileSpec.builder(receiverCls)
            val code = CodeBlock.builder()
                .beginControlFlow("%M", Types.Members.worker)
                .addStatement("val worker: %T = %T()", Types.CompletableDeferred.parameterizedBy(cls), Types.CompletableDeferred)
                .beginControlFlow("receive")
                .addStatement("val (data, callId) = it")
                .addStatement("val reqData = %M<%T>(data)", Types.Members.decodeFromJsObject, cls.reqSealedClass())
                .beginControlFlow("val (result, transfer) = when (reqData)")
                .apply {
                    worker.functions.forEach { f ->
                        val paramCodes = f.parameters.joinToCode {
                            CodeBlock.of("reqData.%N", it.name)
                        }
                        val transfer = CodeBlock.of("(wrappedResponse as? %T)?.transferObj",
                            Types.TransferableResponse
                        )
                        addStatement(
                            """
                            is %T -> { 
                                val rawResult = worker.await().%L(%L)
                                val wrappedResponse = %T(rawResult) 
                                %M(wrappedResponse) to %L  
                            }
                            """.trimIndent(),
                            f.reqClassName,
                            f.name,
                            paramCodes,
                            f.respClassName,
                            Types.Members.encodeToJsObject,
                            transfer
                        )
                    }
                    worker.constructors.forEach { c ->
                        val paramCodes = c.parameters.joinToCode {
                            CodeBlock.of("reqData.%N", it.name)
                        }
                        addStatement(
                            """
                            is %T -> { 
                                worker.complete(%T(%L))
                                %M(Unit) to null
                            }
                        """.trimIndent(),
                            c.reqClassName,
                            cls,
                            paramCodes,
                            Types.Members.encodeToJsObject
                        )
                    }
                }
                .endControlFlow()
                .addStatement("%T(callId, result, transfer)", Types.WebWorkerResponse)
                .endControlFlow()
                .endControlFlow()
                .build()
            f.addFunction(FunSpec.builder("main")
                .addCode(code)
                .addAnnotation(Types.Annotations.optIn(Types.Annotations.ExperimentalWasmInterop))
                .build()
            )
            return f.build()
        }

        private fun ClassName.reqSealedClass(): ClassName {
            return withSuffix("Request")
        }

        private fun ClassName.respSealedClass(): ClassName {
            return withSuffix("Response")
        }

        private fun collectWorkers(workerFileName: String, declarations: Sequence<KSClassDeclaration>): Sequence<WorkerDesc> {
            return declarations.map { decl ->
                val cls = decl.toClassName()
                val sealedReq = cls.reqSealedClass()
                val sealedResp = cls.respSealedClass()
                val annotation = decl.annotations.firstOrNull {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == Types.Annotations.JsWorker.canonicalName
                } ?: error("No JsWorker annotation found")
                val dependencyModules = annotation
                    .arguments.firstOrNull { it.name?.asString() == "dependencyModules" }
                    ?.value as? List<String> ?: error("No dependencyModules arg found. Args: ${annotation.arguments.joinToString { it.name?.asString().toString() }}")
                val jsNameFromAnnotation = annotation.arguments.first { it.name?.asString() == "jsFileName" }.value as String
                val jsName = jsNameFromAnnotation.takeUnless { it.isEmpty() } ?: workerFileName
                val funs = decl.getAllFunctions().filterNot { it.isConstructor() }
                    .filter { (it.parent as? KSClassDeclaration)?.toClassName() != ANY }
                    .filterNot {
                        it.annotations.any {
                            it.annotationType.resolve().toClassName() == Types.Annotations.JsWorkerIgnore
                        }
                    }
                    .map { f ->
                        WorkerFun(
                            f.simpleName.asString(),
                            parameters = f.parameters.toParamList(),
                            returnType = f.returnType!!.toTypeName(),
                            reqClassName = sealedReq.nestedClass(f.simpleName.asString().capitalize()),
                            respClassName = sealedResp.nestedClass(f.simpleName.asString().capitalize()),
                            transferReturn = f.annotations.any { it.annotationType.toTypeName() == Types.Annotations.TransferOwnership }
                        )
                    }

                val constructors = decl.getDeclaredFunctions().filter { it.isConstructor() }
                    .mapIndexed { idx, f ->
                        WorkerFun(
                            "init$idx",
                            parameters = f.parameters.toParamList(),
                            returnType = UNIT,
                            reqClassName = sealedReq.nestedClass("Init$idx"),
                            respClassName = sealedResp.nestedClass("Init$idx"),
                            transferReturn = false
                        )
                    }.takeUnless { decl.classKind == ClassKind.OBJECT }
                WorkerDesc(
                    cls,
                    workerFileName = jsName,
                    functions = funs.toList(),
                    constructors = constructors.orEmpty().toList(),
                    dependencyModules = dependencyModules
                )
            }
        }

        private fun generateRequests(worker: WorkerDesc): FileSpec {
            val sealedCls = worker.cls.reqSealedClass()
            val funSpecs = worker.functions.map { f ->
                buildDataClassOrObject(f.reqClassName, f.parameters)
                    .addAnnotation(Types.Annotations.Serializable)
                    .superclass(sealedCls)
                    .build()
            }.toList()

            val constructorSpecs = worker.constructors.map { f ->
                buildDataClassOrObject(f.reqClassName, f.parameters)
                    .addAnnotation(Types.Annotations.Serializable)
                    .superclass(sealedCls)
                    .build()

            }
            val sealed = TypeSpec.classBuilder(sealedCls)
                .addAnnotation(Types.Annotations.Serializable)
                .addModifiers(KModifier.SEALED)
                .addTypes(funSpecs)
                .addTypes(constructorSpecs)
                .build()
            return FileSpec.builder(sealedCls)
                .addType(sealed)
                .build()
        }

        private fun generateResponses(worker: WorkerDesc): FileSpec {
            val sealedCls = worker.cls.respSealedClass()
            val funSpecs = (worker.functions + worker.constructors).map { f ->
                val builder = buildDataClassOrObject(f.respClassName, listOf(ParamInfo("value", f.returnType, false)), override = setOf("value"))
                    .addAnnotation(Types.Annotations.Serializable)
                    .superclass(sealedCls)
                    .addSuperinterface(Types.CallResponse.parameterizedBy(f.returnType))

                if (f.transferReturn) {
                    val transfer = if (f.returnType == Types.ArrayBuffer) "value" else "value.buffer"
                    builder.addSuperinterface(Types.TransferableResponse)
                    builder.addProperty(
                        PropertySpec.builder("transferObj", Types.JsAny)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(FunSpec.getterBuilder().addCode("return $transfer").build())
                            .build()
                    )
                }
                builder.build()
            }
            val sealed = TypeSpec.classBuilder(sealedCls)
                .addAnnotation(Types.Annotations.Serializable)
                .addModifiers(KModifier.SEALED)
                .addTypes(funSpecs)
                .build()
            return FileSpec.builder(sealedCls)
                .addType(sealed)
                .build()
        }
    }
}