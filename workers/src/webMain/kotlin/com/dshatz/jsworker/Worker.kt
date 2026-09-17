@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalAtomicApi::class)

package com.dshatz.jsworker

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.w3c.dom.ErrorEvent
import org.w3c.dom.MODULE
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import org.w3c.dom.WorkerOptions
import org.w3c.dom.WorkerType
import org.w3c.dom.events.Event
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.toJsString
import kotlin.js.unsafeCast


fun createWorkerFromModule(scriptURL: String): CompletableDeferred<Worker> {
    println("Creating worker $scriptURL")
    val worker = Worker(scriptURL, WorkerOptions(WorkerType.MODULE))
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
/*external open class Worker(
    url: String,
    options: WorkerOptions = definedExternally
): EventTarget,
    AbstractWorker {
    var onmessageerror: ((Event) -> Unit)?
    override var onerror: ((Event) -> Unit)?
    fun terminate()
    fun postMessage(message: JsAny?, transfer: JsAny = definedExternally)
}*/

val workerScope = CoroutineScope(Dispatchers.Default)

inline fun <reified T, reified R> Worker.sendIgnoreResult(data: T) {
    workerScope.launch {
        try {
            println("sendIgnoreResult")
            val result: R = send<T, R>(data)
            println("Ignoring result $result")
        } catch (e: Throwable) {
            println("Error in sendIgnoreResult: ${e.message}")
        }
    }
}

inline suspend fun <reified T, reified R> Worker.send(data: T): R = suspendCancellableCoroutine<R> { continuation ->
    val callId = callCounter.incrementAndFetch()
    println("Making call callId = $callId, data = $data")
    val listener = { event: Event ->
        if (event is MessageEvent) {
            val responseText = (event.data as JsString).toString()
            val parsed = workerJson.decodeFromString<WorkerResponse<JsonElement>>(responseText)
            if (parsed.callId == callId) {
                val response = workerJson.decodeFromJsonElement<R>(parsed.response)
                continuation.resume(response)
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
    println("Serializing message $data")
    val message = workerJson.encodeToString<WorkerRequest<T>>(
        WorkerRequest(
            callId,
            data
        )
    )
    println("Sending message $message")
    this.postMessage(message.toJsString())
}

val callCounter = kotlin.concurrent.atomics.AtomicInt(0)

@Serializable
data class WorkerRequest<T>(
    val callId: Int,
    val request: T
)

@Serializable
data class WorkerResponse<R>(
    val callId: Int,
    val response: R
)

val workerJson = Json