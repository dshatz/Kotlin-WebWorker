package com.dshatz.jsworker.model

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

data class WorkerFun(
    val name: String,
    val parameters: List<ParamInfo>,
    val returnType: TypeName,
    val reqClassName: ClassName
)