package com.dshatz.jsworker.model

import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName

data class ParamInfo(
    val name: String,
    val type: TypeName
) {
    fun toParameterSpec(): ParameterSpec {
        return ParameterSpec(name, type)
    }
}