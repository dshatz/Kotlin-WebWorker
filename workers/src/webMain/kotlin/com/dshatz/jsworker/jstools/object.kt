@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.jsworker.jstools

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

@JsFun("(obj, key) => obj[key]")
external fun getPropertyValue(obj: JsAny, key: String): JsAny?

fun JsAny.asJsObject(): Map<String, JsAny> {
    val keys = getObjectKeys(this)
    val length = getArrayLength(keys)
    val map = mutableMapOf<String, JsAny>()
    for (i in 0 until length) {
        val key = getArrayItem(keys, i)
        val value = getPropertyValue(this, key)
        if (value != null) {
            map[key] = value
        }
    }
    return map
}

@JsFun("(obj) => Object.keys(obj)")
private external fun getObjectKeys(obj: JsAny): JsAny

@JsFun("(arr) => arr.length")
private external fun getArrayLength(arr: JsAny): Int

@JsFun("(arr, index) => arr[index]")
private external fun getArrayItem(arr: JsAny, index: Int): String


@JsFun("(obj) => obj instanceof ArrayBuffer")
external fun isArrayBuffer(obj: JsAny?): Boolean