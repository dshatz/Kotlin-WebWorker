package com.dshatz.jsworker

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName

object Types {
    val pkg = "com.dshatz.jsworker"

    object Annotations {
        val JsWorker = ClassName(pkg, "JSWorker")
        val JsWorkerIgnore = ClassName(pkg, "JSWorkerIgnore")
        val Serializable = ClassName("kotlinx.serialization", "Serializable")
        val TransferOwnership = ClassName(pkg, "TransferOwnership")
        val ExperimentalWasmInterop = ClassName("kotlin.js", "ExperimentalWasmJsInterop")
        val OptIn = ClassName("kotlin", "OptIn")

        fun optIn(cls: TypeName): AnnotationSpec {
            return AnnotationSpec.builder(OptIn).addMember("%T::class", cls).build()
        }
    }

    object Members {
        val worker = MemberName(pkg, "worker")
        val send = MemberName(pkg, "send")
        val sendIgnoreResult = MemberName(pkg, "sendIgnoreResult")
        val createWorkerFromModule = MemberName(pkg, "createWorkerFromModule")

        val importModule = MemberName(pkg, "importModule")
        val await = MemberName("kotlinx.coroutines", "await")
        val transferableOrNull = MemberName(pkg, "transferableOrNull")
        val workerJson = MemberName(pkg, "workerJson")
        val decodeFromJsObject = MemberName(pkg, "decodeFromJsObject")
        val encodeToJsObject = MemberName(pkg, "encodeToJsObject")
    }

    val Worker = ClassName("org.w3c.dom", "Worker")
    val CompletableDeferred = ClassName("kotlinx.coroutines", "CompletableDeferred")

    val CallResponse = ClassName(pkg, "CallResponse")

}