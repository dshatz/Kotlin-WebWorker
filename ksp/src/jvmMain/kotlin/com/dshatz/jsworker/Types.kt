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
        val decodeFromJsObject = MemberName("${pkg}.serializers", "decodeFromJsObject")
        val encodeToJsObject = MemberName("${pkg}.serializers", "encodeToJsObject")
    }

    val Worker = ClassName("org.w3c.dom", "Worker")
    val CompletableDeferred = ClassName("kotlinx.coroutines", "CompletableDeferred")

    val CallResponse = ClassName(pkg, "CallResponse")
    val TransferableResponse = ClassName(pkg, "TransferableResponse")
    val WebWorkerResponse = ClassName(pkg, "WebWorkerResponse")

    val JsAny = ClassName("kotlin.js", "JsAny")

    val ArrayBuffer = ClassName("org.khronos.webgl", "ArrayBuffer")

}