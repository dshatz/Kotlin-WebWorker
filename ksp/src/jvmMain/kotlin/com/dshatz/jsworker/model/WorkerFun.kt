package com.dshatz.jsworker.model

import com.dshatz.jsworker.Types
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.joinToCode

data class WorkerFun(
    val name: String,
    val parameters: List<ParamInfo>,
    val returnType: TypeName,
    val transferReturn: Boolean,
    val reqClassName: ClassName,
    val respClassName: ClassName,
) {
    fun constructorBuilder(): FunSpec.Builder {
        return FunSpec.constructorBuilder()
            .addParameters(parameters.map { it.toParameterSpec() })
    }

    fun funBuilder(): FunSpec.Builder {
        return FunSpec.builder(name)
            .addParameters(parameters.map { it.toParameterSpec() })
    }

    fun paramTransferArray(): CodeBlock {
        val values = parameters.filter { it.transfer }.joinToCode {
            if (it.type == Types.ArrayBuffer) CodeBlock.of("%N", it.name)
            else CodeBlock.of("%N.buffer", it.name)
        }
        return CodeBlock.of("listOf<%T>(%L)", Types.JsAny, values)
    }
}