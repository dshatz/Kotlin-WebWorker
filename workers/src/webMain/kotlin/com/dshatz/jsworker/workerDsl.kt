@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.jsworker

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.DedicatedWorkerGlobalScope
import org.w3c.dom.url.URLSearchParams
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsString

external val self: DedicatedWorkerGlobalScope

fun worker(block: WorkerScope.() -> Unit) {
    try {
        val scope = WorkerScope(self)
        block(scope)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

class WorkerScope(private val self: DedicatedWorkerGlobalScope) {
    val workerId = URLSearchParams(self.location.search.toJsString()).get("id") ?: "Unknown worker"

    fun receive(block: suspend (String) -> String) {
        self.onmessage = { messageEvent ->
            GlobalScope.launch {
                self.postMessage(block(messageEvent.data.toString()).toJsString())
            }
        }
    }
}