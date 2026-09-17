package com.dshatz.jsworker

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

object Types {
    val pkg = "com.dshatz.jsworker"

    object Annotations {
        val JsWorker = ClassName(pkg, "JSWorker")
        val Serializable = ClassName("kotlinx.serialization", "Serializable")
    }

    object Members {
        val worker = MemberName(pkg, "worker")
        val send = MemberName(pkg, "send")
        val sendIgnoreResult = MemberName(pkg, "sendIgnoreResult")
        val createWorkerFromModule = MemberName(pkg, "createWorkerFromModule")
    }

    val WorkerRequest = ClassName(pkg, "WorkerRequest")
    val WorkerResponse = ClassName(pkg, "WorkerResponse")
    val Worker = ClassName("org.w3c.dom", "Worker")
    val CompletableDeferred = ClassName("kotlinx.coroutines", "CompletableDeferred")
}