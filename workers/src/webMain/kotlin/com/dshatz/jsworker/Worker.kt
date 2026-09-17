@file:OptIn(ExperimentalWasmJsInterop::class, ExperimentalAtomicApi::class)

package com.dshatz.jsworker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import org.w3c.dom.AbstractWorker
import org.w3c.dom.ErrorEvent
import org.w3c.dom.MODULE
import org.w3c.dom.MessageEvent
import org.w3c.dom.WorkerOptions
import org.w3c.dom.WorkerType
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.EventTarget
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.definedExternally
import kotlin.js.toJsString
import kotlin.js.unsafeCast


fun createWorkerFromModule(scriptURL: String): Worker {
    println("Creating worker $scriptURL")
    return Worker(scriptURL, WorkerOptions(WorkerType.MODULE))
}
public external open class Worker(
    scriptURL: String,
    options: WorkerOptions = definedExternally
) : EventTarget,
    AbstractWorker {
    var onmessage: ((MessageEvent) -> JsAny)?
    var onmessageerror: ((Event) -> Unit)?
    override var onerror: ((Event) -> Unit)?
    fun terminate()
    fun postMessage(message: JsAny?, transfer: JsAny = definedExternally)
}

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
    val listener = object: EventListener {
        override fun handleEvent(event: Event) {
            event as MessageEvent
            val responseText = (event.data as JsString).toString()
            val parsed = workerJson.decodeFromString<WorkerResponse<JsonElement>>(responseText)
            if (parsed.callId == callId) {
                val response = workerJson.decodeFromJsonElement<R>(parsed.response)
                continuation.resume(response)
            }
        }
    }
    addEventListener("message", listener)
    this.onerror = { event ->
        continuation.resumeWithException(RuntimeException("onerror: " + event.unsafeCast<ErrorEvent>().message))
    }
    this.onmessageerror = { event ->
        continuation.resumeWithException(RuntimeException("onmessageerror: " + event.unsafeCast<ErrorEvent>().message))
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