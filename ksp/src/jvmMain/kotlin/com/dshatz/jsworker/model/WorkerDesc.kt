package com.dshatz.jsworker.model

import com.squareup.kotlinpoet.ClassName

data class WorkerDesc(
    val cls: ClassName,
    val workerFileName: String,
    val functions: List<WorkerFun>,
    val constructors: List<WorkerFun>
)

