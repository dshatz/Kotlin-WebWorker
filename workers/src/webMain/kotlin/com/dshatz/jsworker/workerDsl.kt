@file:OptIn(ExperimentalWasmJsInterop::class)

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

    fun receive(block: suspend (Pair<JsAny, Int>) -> Pair<JsAny, Int>) {
        self.postMessage("READY".toJsString())
        self.onmessage = { messageEvent ->
            scope.launch {
                val result = async {
                    println("Request ${stringify(messageEvent.data)}")
                    val obj = messageEvent.data?.asJsObject() ?: error("No message data received.")
                    val jsData = obj["data"] ?: emptyJsObject()
                    val callId = getPropertyValue(messageEvent.data!!, "callId").toString().toInt()
                    val result = block(jsData to callId)
                    println("Response (${result.second}) ${result.first.asJsObject()}")
                    result

                }
                val (response, callId) = result.await()
                self.postMessage(makeJsObject(callId, response).also {
                    println("Sending ${stringify(it)}")
                })
            }
        }
    }
}

@JsFun("(obj) => JSON.stringify(obj)")
private external fun stringify(obj: JsAny?): String

fun emptyJsObject(): JsAny = js("({})")
