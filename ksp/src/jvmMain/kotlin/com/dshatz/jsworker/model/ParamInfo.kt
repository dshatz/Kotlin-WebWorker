package com.dshatz.jsworker.model

import com.squareup.kotlinpoet.TypeName

data class ParamInfo(
    val name: String,
    val type: TypeName
)