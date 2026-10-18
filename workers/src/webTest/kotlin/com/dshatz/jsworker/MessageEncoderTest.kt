package com.dshatz.jsworker

import com.dshatz.jsworker.decodeFromJsObject
import com.dshatz.jsworker.encodeToJsObject
import com.dshatz.jsworker.jstools.asJsObject
import com.dshatz.jsworker.jstools.getPropertyValue
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.test.Test

@Serializable
data class TestPayload(
    val id: Int,
    val name: String,
    val active: Boolean,
    val count: Long,
    @Contextual
    val buffer: ArrayBuffer,
    @Contextual
    val arr: Int8Array,
    val items: List<String>,
    val attributes: Map<String, String>,
    val nested: Nested
)

@Serializable
data class Nested(
    val name: String
)

class MessageEncoderTest {
    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun testEncodeToJsObject() {
        val arr = Int8Array(10)
        val payload = TestPayload(
            id = 42,
            name = "test",
            active = true,
            count = 1234567890123L,
            buffer = arr.buffer,
            arr = arr,
            items = listOf("a", "b"),
            attributes = mapOf("key" to "val"),
            nested = Nested("nested")
        )

        val jsObj = encodeToJsObject(payload)

        getPropertyValue(jsObj, "id").toString().toInt() shouldBe 42
        getPropertyValue(jsObj, "name").toString() shouldBe "test"
        getPropertyValue(jsObj, "active").toString().toBoolean() shouldBe true
        getPropertyValue(jsObj, "buffer") shouldBe arr.buffer

        val map = jsObj.asJsObject()
        map["id"]?.toString()?.toInt() shouldBe 42
        map["name"]?.toString() shouldBe "test"
        map["nested"]?.asJsObject()?.get("name").toString() shouldBe "nested"
        println(map)
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun testRoundTrip() {
        val arr = Int8Array(10)
        val payload = TestPayload(
            id = 100,
            name = "roundtrip",
            active = false,
            count = 9876543210L,
            buffer = arr.buffer,
            arr = arr,
            items = listOf("x", "y"),
            attributes = mapOf("a" to "b"),
            nested = Nested("inner")
        )

        val jsObj = encodeToJsObject(payload)
        println("nested obj stringified: ${stringify(getPropertyValue(jsObj, "nested")!!)}")
        val decoded = decodeFromJsObject<TestPayload>(jsObj)

        decoded.id shouldBe payload.id
        decoded.name shouldBe payload.name
        decoded.active shouldBe payload.active
        decoded.count shouldBe payload.count
        decoded.items shouldBe payload.items
        decoded.attributes shouldBe payload.attributes
        decoded.nested.name shouldBe payload.nested.name
        decoded.buffer shouldBe payload.buffer
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(obj) => JSON.stringify(obj)")
private external fun stringify(obj: JsAny?): String
