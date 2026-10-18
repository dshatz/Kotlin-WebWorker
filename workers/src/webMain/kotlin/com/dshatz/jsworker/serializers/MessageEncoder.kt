@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.jsworker

import com.dshatz.jsworker.jstools.isArrayBuffer
import com.dshatz.jsworker.serializers.Int8ArraySerializer
import kotlinx.coroutines.NonCancellable.key
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.serializer
import org.khronos.webgl.ArrayBuffer
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsReference
import kotlin.js.toJsString
import kotlin.js.unsafeCast

@OptIn(ExperimentalSerializationApi::class)
object ArrayBufferSerializer : KSerializer<ArrayBuffer> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ArrayBuffer", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ArrayBuffer) {
        if (encoder is MessageEncoder) {
            encoder.encodeValue(value)
        } else {
            (encoder as AbstractEncoder).encodeValue(value)
        }
    }

    override fun deserialize(decoder: Decoder): ArrayBuffer {
        if (decoder is MessageDecoder) {
            val value = decoder.currentValue() ?: error("ArrayBuffer value is missing")
            if (isArrayBuffer(value)) return value.unsafeCast()
            else error("Value is not an ArrayBuffer: $value")
        }
        error("Unsupported decoder for ArrayBuffer")
    }
}

internal val jsSerialModule: SerializersModule = SerializersModule {
    contextual(ArrayBufferSerializer)
    contextual(Int8ArraySerializer)
}

@OptIn(ExperimentalSerializationApi::class)
class MessageEncoder(
    private val isArray: Boolean = false,
    private val isMap: Boolean = false
) : AbstractEncoder() {
    override val serializersModule: SerializersModule = jsSerialModule

    val resultObj: JsAny = if (isArray) emptyJsArray() else emptyJsObject()
    private var currentKey: String? = null
    private var currentIndex: Int = 0
    private var mapKey: String? = null

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        currentIndex = index
        if (!isArray && !isMap) {
            currentKey = descriptor.getElementName(index)
        }
        return true
    }

    override fun encodeValue(value: Any) {
        val jsValue = toJsAny(value)
        if (isArray) {
            setArrayItem(resultObj, currentIndex, jsValue)
        } else if (isMap) {
            if (currentIndex % 2 == 0) {
                mapKey = value.toString()
            } else {
                val k = mapKey
                if (k != null) {
                    setObjProp(resultObj, k, jsValue)
                    mapKey = null
                }
            }
        } else {
            if (currentKey != null) {
                val key = currentKey!!
                println("Setting $key = $jsValue")
                setObjProp(resultObj, key, jsValue)
            }
            currentKey = null
        }
    }

    override fun encodeNull() {
        if (isArray) {
            setArrayItem(resultObj, currentIndex, null)
        } else if (isMap) {
            if (currentIndex % 2 != 0) {
                val k = mapKey
                if (k != null) {
                    setObjProp(resultObj, k, null)
                    mapKey = null
                }
            }
        } else {
            val key = currentKey ?: return
            setObjProp(resultObj, key, null)
            currentKey = null
        }
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        val enumName = enumDescriptor.getElementName(index)
        encodeString(enumName)
    }

    override fun encodeBoolean(value: Boolean) {
        encodeValue(value)
    }

    override fun encodeByte(value: Byte) {
        encodeValue(value)
    }

    override fun encodeShort(value: Short) {
        encodeValue(value)
    }

    override fun encodeInt(value: Int) {
        encodeValue(value)
    }

    override fun encodeLong(value: Long) {
        encodeValue(value)
    }

    override fun encodeFloat(value: Float) {
        encodeValue(value)
    }

    override fun encodeDouble(value: Double) {
        encodeValue(value)
    }

    override fun encodeChar(value: Char) {
        encodeValue(value.toString())
    }

    override fun encodeString(value: String) {
        if (isMap && currentIndex % 2 == 0) {
            mapKey = value
        } else {
            encodeValue(value)
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (currentKey == null && !isArray && !isMap) {
            return this
        }

        val kind = descriptor.kind
        val isSubArray = kind is StructureKind.LIST
        val isSubMap = kind is StructureKind.MAP
        val nestedEncoder = MessageEncoder(isArray = isSubArray, isMap = isSubMap)

        if (isArray) {
            setArrayItem(resultObj, currentIndex, nestedEncoder.resultObj)
        } else if (isMap) {
            if (currentIndex % 2 != 0) {
                val k = mapKey
                if (k != null) {
                    setObjProp(resultObj, k, nestedEncoder.resultObj)
                    mapKey = null
                }
            }
        } else {
            val key = currentKey
            if (key != null) {
                setObjProp(resultObj, key, nestedEncoder.resultObj)
                currentKey = null
            }
        }
        return nestedEncoder
    }

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    private fun toJsAny(value: Any): JsAny? {
        when (value) {
            is Int -> return boxInt(value)
            is Long -> return boxDouble(value.toDouble())
            is Double -> return boxDouble(value)
            is Float -> return boxDouble(value.toDouble())
            is Boolean -> return boxBoolean(value)
            is String -> return value.toJsString()
            is Byte -> return boxInt(value.toInt())
            is Short -> return boxInt(value.toInt())
            is Char -> return value.toString().toJsString()
        }
        return value.toJsReference()
    }
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
private fun Any.asJsAny(): JsAny? = this as? JsAny

@JsFun("() => ([])")
private external fun emptyJsArray(): JsAny

@JsFun("(obj, key, value) => { obj[key] = value; }")
private external fun setObjProp(obj: JsAny, key: String, value: JsAny?)

@JsFun("(arr, index, value) => { arr[index] = value; }")
private external fun setArrayItem(arr: JsAny, index: Int, value: JsAny?)

@JsFun("(v) => v")
private external fun boxInt(v: Int): JsAny

@JsFun("(v) => v")
private external fun boxDouble(v: Double): JsAny

@JsFun("(v) => v")
private external fun boxBoolean(v: Boolean): JsAny

inline fun <reified T> encodeToJsObject(serializer: SerializationStrategy<T>, value: T): JsAny {
    val encoder = MessageEncoder()
    serializer.serialize(encoder, value)
    return encoder.resultObj
}

inline fun <reified T> encodeToJsObject(value: T): JsAny {
    val serializer = serializer<T>()
    return encodeToJsObject(serializer, value)
}
