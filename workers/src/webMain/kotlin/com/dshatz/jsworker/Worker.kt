@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalAtomicApi::class)

package com.dshatz.jsworker

import com.dshatz.jsworker.jstools.getPropertyValue
import com.dshatz.jsworker.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.khronos.webgl.ArrayBuffer
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
import kotlin.js.js
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
    val listener = { event: Event ->
        if (event is MessageEvent) {
            val obj = event.data ?: emptyJsObject()
            val responseCallId = getPropertyValue(obj, "callId").toString().toIntOrNull()
            val responseJs = getPropertyValue(obj, "data") ?: error("no data key in message: $obj")
            if (responseCallId == callId) {
                val response = decodeFromJsObject<R>(responseJs)
                continuation.resume(response.value)
            }
        } else {
            println("WARN: Event is $event")
        }
    }
    addEventListener("message", listener)
    this.onerror = { event ->
        continuation.resumeWithException(RuntimeException("onerror: " + event.unsafeCast<ErrorEvent>().message))
    }
    continuation.invokeOnCancellation {
        removeEventListener("message", listener)
    }
    val msg = makeMessage(callId, data)
    this.postMessage(msg, transfer.toJsArray())
}

@JsFun("(obj) => JSON.stringify(obj, (key, value) => typeof value === 'bigint' ? value.toString() : value)")
external fun stringifyJs(obj: JsAny?): String

@JsFun("(obj) => typeof obj === 'object' && obj !== null && !('constructor' in obj && obj.constructor.name === 'WasmStructObject') || typeof obj === 'function' || typeof obj === 'symbol'")
external fun isJsAny(obj: JsAny?): Boolean

@JsFun("(obj) => obj")
external fun unsafeCastToJsAny(obj: JsAny?): JsAny?

fun transferableOrNull(obj: JsAny): ArrayBuffer? {
    return toBufferOrNull(obj)
}

interface CallResponse<T> {
    val value: T
}

@JsFun("""
    (obj) => {
        // If it's already an ArrayBuffer, return it
        if (obj instanceof ArrayBuffer) {
            return obj;
        }
        // If it's a typed array or DataView (ArrayBufferView), return its buffer
        if (ArrayBuffer.isView(obj)) {
            return obj.buffer;
        }
        return null;
    }
""")
private external fun toBufferOrNull(obj: JsAny): ArrayBuffer?

inline fun <reified T> makeMessage(callId: Int, data: T): JsAny {
    val encoded = encodeToJsObject(data)
    return makeJsObject(callId, encoded)
}

@JsFun("(callId, data) => ({ callId: callId, data: data })")
external fun makeJsObject(callId: Int, data: JsAny): JsAny

val callCounter = AtomicInt(0)

@Serializable
data class WorkerRequest<T>(
    val callId: Int,
    val request: T,
)

@Serializable
data class WorkerResponse<R>(
    val callId: Int,
    val response: R,
)
