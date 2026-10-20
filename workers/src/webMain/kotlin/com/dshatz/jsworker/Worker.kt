@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalAtomicApi::class)

package com.dshatz.jsworker

import com.dshatz.jsworker.jstools.getPropertyValue
import com.dshatz.jsworker.serializers.decodeFromJsObject
import com.dshatz.jsworker.serializers.encodeToJsObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.ErrorEvent
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import org.w3c.dom.events.Event
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.toJsArray
import kotlin.js.unsafeCast


fun createWorkerFromModule(scriptURL: String): CompletableDeferred<Worker> {
    println("Creating worker $scriptURL")
    val worker = Worker(scriptURL, /*WorkerOptions(WorkerType.MODULE)*/)
    val result = CompletableDeferred<Worker>()
    worker.addEventListener("message") { e ->
        if (result.isActive && e is MessageEvent) {
            val data = e.data
            if (data != null && (data as? JsString)?.toString() == "READY") {
                println("Worker initialized")
                result.complete(worker)
            }
            e
        }
    }
    return result
}

val workerScope = CoroutineScope(Dispatchers.Default)

inline fun <reified T, reified R: CallResponse<V>, reified V> Worker.sendIgnoreResult(data: T, transfer: List<JsAny> = emptyList()) {
    workerScope.launch {
        try {
            val result: V = send<T, R, V>(data, transfer)
        } catch (e: Throwable) {
            println("Error in sendIgnoreResult: ${e.message}")
        }
    }
}

suspend inline fun <reified T, reified R: CallResponse<V>, reified V> Worker.send(data: T, transfer: List<JsAny> = emptyList()): V = suspendCancellableCoroutine<V> { continuation ->
    val callId = callCounter.incrementAndFetch()
    lateinit var listener: (Event) -> Unit
    listener = { event: Event ->
        if (event is MessageEvent) {
            val obj = event.data ?: emptyJsObject()
            val responseCallId = getPropertyValue(obj, "callId").toString().toIntOrNull()
            val responseJs = getPropertyValue(obj, "data") ?: error("no data key in message: $obj")
            if (responseCallId == callId) {
                removeEventListener("message", listener)
                val response = decodeFromJsObject<R>(responseJs)
                continuation.resume(response.value)
            }
        } else {
            println("WARN: Unknown event type: ${event.type}")
        }
    }
    addEventListener("message", listener)
    lateinit var errorListener: (Event) -> Unit
    errorListener = { event ->
        removeEventListener("error", errorListener)
        continuation.resumeWithException(RuntimeException("onerror: " + event.unsafeCast<ErrorEvent>().message))
    }
    continuation.invokeOnCancellation {
        removeEventListener("message", listener)
        removeEventListener("error", listener)
    }
    val msg = makeMessage(callId, data)
    this.postMessage(msg, transfer.toJsArray())
}

interface CallResponse<T> {
    val value: T
}

interface TransferableResponse {
    val transferObj: JsAny
}

inline fun <reified T> makeMessage(callId: Int, data: T): JsAny {
    val encoded = encodeToJsObject(data)
    return makeJsObject(callId, encoded)
}

@JsFun("(callId, data) => ({ callId: callId, data: data })")
external fun makeJsObject(callId: Int, data: JsAny): JsAny

val callCounter = AtomicInt(0)