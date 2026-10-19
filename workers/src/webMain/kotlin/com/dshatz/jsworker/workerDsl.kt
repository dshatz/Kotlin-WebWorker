package com.dshatz.jsworker

import com.dshatz.jsworker.jstools.asJsObject
import com.dshatz.jsworker.jstools.getPropertyValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.w3c.dom.DedicatedWorkerGlobalScope
import org.w3c.dom.url.URLSearchParams
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.js
import kotlin.js.toJsArray
import kotlin.js.toJsString

external val self: DedicatedWorkerGlobalScope

fun worker(block: suspend WorkerScope.() -> Unit) {
    val scope = CoroutineScope(SupervisorJob())
    try {
        scope.launch {
            val scope = WorkerScope(self, scope)
            block(scope)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        println("Killing worker: ${e.message}")
        scope.cancel()
    }
}

class WorkerScope(private val self: DedicatedWorkerGlobalScope, private val scope: CoroutineScope) {
    val params = URLSearchParams(self.location.search.toJsString())

    fun receive(block: suspend (Pair<JsAny, Int>) -> WebWorkerResponse) {
        self.postMessage("READY".toJsString())
        self.onmessage = { messageEvent ->
            scope.launch {
                val result = async {
                    val obj = messageEvent.data?.asJsObject() ?: error("No message data received.")
                    val jsData = obj["data"] ?: emptyJsObject()
                    val callId = getPropertyValue(messageEvent.data!!, "callId").toString().toInt()
                    val result = block(jsData to callId)
                    result
                }
                val (callId, response, transfer) = result.await()
                val transferArr = listOfNotNull(transfer).toJsArray()
                println("Transfer arr $transferArr")
                self.postMessage(makeJsObject(callId, response), transferArr)
            }
        }
    }
}

data class WebWorkerResponse(val callId: Int, val data: JsAny, val transfer: JsAny?)

fun emptyJsObject(): JsAny = js("({})")
