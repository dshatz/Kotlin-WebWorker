package com.dshatz.jsworker.model

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeName

data class WorkerFun(
    val name: String,
    val parameters: List<ParamInfo>,
    val returnType: TypeName,
    val reqClassName: ClassName
) {
    fun constructorBuilder(): FunSpec.Builder {
        return FunSpec.constructorBuilder()
            .addParameters(parameters.map { it.toParameterSpec() })
    }

    fun funBuilder(): FunSpec.Builder {
        return FunSpec.builder(name)
            .addParameters(parameters.map { it.toParameterSpec() })
    }
}