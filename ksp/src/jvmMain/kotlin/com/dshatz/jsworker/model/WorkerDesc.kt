package com.dshatz.jsworker.model

import com.dshatz.jsworker.withSuffix
import com.squareup.kotlinpoet.ClassName

data class WorkerDesc(
    val cls: ClassName,
    val proxyClass: ClassName = cls.withSuffix("WebWorker"),
    val workerFileName: String,
    val functions: List<WorkerFun>,
    val constructors: List<WorkerFun>,
    val dependencyModules: List<String>
) {
    val entrypointFileName: String = "${cls.simpleName}_entrypoint"
}

