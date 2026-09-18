package com.dshatz.jsworker

import com.dshatz.jsworker.Types.Members.send
import com.dshatz.jsworker.model.ParamInfo
import com.dshatz.jsworker.model.WorkerDesc
import com.dshatz.jsworker.model.WorkerFun
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
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
                    generateJsReceiverCode(it),
                    generateProxy(it)
                )
            }.forEach {
                it.writeTo(codeGenerator, deps)
            }

            return emptyList()
        }

        private fun generateProxy(worker: WorkerDesc): FileSpec {
            val proxyCls = worker.proxyClass
            val f = FileSpec.builder(proxyCls)
            val type = TypeSpec.classBuilder(proxyCls)
                .addProperty(
                    PropertySpec.builder("worker", Types.CompletableDeferred.parameterizedBy(Types.Worker))
                        .initializer("%M(%S)", Types.Members.createWorkerFromModule, "workers/${worker.workerFileName}")
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
                    .addStatement("return readyWorker.await().%M<%T, %T>(%L)", Types.Members.send, worker.cls.reqSealedClass(), f.returnType, createReq)
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
                            "w.%M<%T, %T>(%L)",
                            Types.Members.sendIgnoreResult,
                            worker.cls.reqSealedClass(),
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
            return f.build()
        }

        private fun generateJsReceiverCode(worker: WorkerDesc): FileSpec {
            val cls = worker.cls
            val receiverCls = cls.withSuffix("_Receiver")
            val f = FileSpec.builder(receiverCls)
            val code = CodeBlock.builder()
                .beginControlFlow("%M", Types.Members.worker)
                .addStatement("val json = kotlinx.serialization.json.Json")
                .addStatement("val worker: %T = %T()", Types.CompletableDeferred.parameterizedBy(cls), Types.CompletableDeferred)
                .beginControlFlow("receive")
                .addStatement("val req = json.decodeFromString<%T<%T>>(it)", Types.WorkerRequest, cls.reqSealedClass())
                .addStatement("val reqData = req.request")
                .beginControlFlow("val result = when (reqData)")
                .apply {
                    worker.functions.forEach { f ->
                        val paramCodes = f.parameters.joinToCode {
                            CodeBlock.of("reqData.%N", it.name)
                        }
                        addStatement("""
                            is %T -> { 
                                json.encodeToString(%T(req.callId, worker.await().%L(%L))) 
                            }
                            """.trimIndent(), f.reqClassName, Types.WorkerResponse, f.name, paramCodes)
                    }
                    worker.constructors.forEach { c ->
                        val paramCodes = c.parameters.joinToCode {
                            CodeBlock.of("reqData.%N", it.name)
                        }
                        addStatement("""
                            is %T -> { 
                                worker.complete(%T(%L))
                                json.encodeToString(%T(req.callId, Unit))
                            }
                        """.trimIndent(), c.reqClassName, cls, paramCodes, Types.WorkerResponse)
                    }
                }
                .endControlFlow()
                .addStatement("result")
                .endControlFlow()
                .endControlFlow()
                .build()
            f.addFunction(FunSpec.builder("main")
                .addCode(code)
                .build()
            )
            return f.build()
        }

        private fun ClassName.reqSealedClass(): ClassName {
            return withSuffix("Request")
        }

        private fun collectWorkers(workerFileName: String, declarations: Sequence<KSClassDeclaration>): Sequence<WorkerDesc> {
            return declarations.map { decl ->
                val cls = decl.toClassName()
                val sealed = cls.reqSealedClass()
                val funs = decl.getDeclaredFunctions().filterNot { it.isConstructor() }
                    .filter { Modifier.SUSPEND in it.modifiers }
                    .map { f ->
                        WorkerFun(
                            f.simpleName.asString(),
                            parameters = f.parameters.toParamList(),
                            returnType = f.returnType!!.toTypeName(),
                            reqClassName = sealed.nestedClass(f.simpleName.asString().uppercase())
                        )
                    }

                val constructors = decl.getDeclaredFunctions().filter { it.isConstructor() }
                    .mapIndexed { idx, f ->
                        WorkerFun(
                            "init$idx",
                            parameters = f.parameters.toParamList(),
                            returnType = UNIT,
                            reqClassName = sealed.nestedClass("Init$idx")
                        )
                    }
                WorkerDesc(
                    cls,
                    workerFileName = workerFileName,
                    functions = funs.toList(),
                    constructors = constructors.toList()
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
    }
}