@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.jsworker

import com.dshatz.jsworker.jstools.asJsObject
import com.dshatz.jsworker.jstools.getPropertyValue
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.khronos.webgl.ArrayBuffer
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

@OptIn(ExperimentalSerializationApi::class)
class MessageDecoder(private val sourceObj: JsAny, private val isMap: Boolean = false) : AbstractDecoder() {
    override val serializersModule: SerializersModule = jsSerialModule

    private val map: Map<String, JsAny> = sourceObj.asJsObject()
    private val mapKeys: List<String> = map.keys.toList()
    private val mapValues: List<JsAny> = map.values.toList()
    private var elementIndex = 0
    private var currentKey: String? = null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val kind = descriptor.kind
        if (kind is StructureKind.LIST) {
            val lengthVal = getPropertyValue(sourceObj, "length")
            val length = lengthVal?.toString()?.toInt() ?: map.size
            if (elementIndex < length) {
                currentKey = elementIndex.toString()
                return elementIndex++
            }
            return CompositeDecoder.DECODE_DONE
        } else if (isMap) {
            val totalElements = map.size * 2
            if (elementIndex < totalElements) {
                return elementIndex++
            }
            return CompositeDecoder.DECODE_DONE
        } else {
            if (elementIndex < descriptor.elementsCount) {
                val idx = elementIndex++
                currentKey = descriptor.getElementName(idx)
                return idx
            }
            return CompositeDecoder.DECODE_DONE
        }
    }

    internal fun currentValue(): JsAny? {
        val key = currentKey ?: return null
        return map[key] ?: getPropertyValue(sourceObj, key)
    }

    override fun decodeValue(): Any {
        if (isMap) {
            val idx = elementIndex - 1
            if (idx % 2 != 0) {
                val value = mapValues[idx / 2]
                if (value is ArrayBuffer) return value
                val buffer = transferableOrNull(value)
                if (buffer != null) return buffer
                return value
            } else {
                return mapKeys[idx / 2]
            }
        }
        val value = currentValue() ?: error("Value for key '$currentKey' is null or missing")
        if (value is ArrayBuffer) return value
        val buffer = transferableOrNull(value)
        if (buffer != null) return buffer
        return value
    }

    override fun decodeNotNullMark(): Boolean {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2] != null
        }
        return currentValue() != null
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeBoolean(): Boolean {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().toBoolean()
        }
        val v = currentValue() ?: return false
        return v.toString().toBoolean()
    }

    override fun decodeByte(): Byte {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().toByte()
        }
        val v = currentValue() ?: return 0
        return v.toString().toByte()
    }

    override fun decodeShort(): Short {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().toShort()
        }
        val v = currentValue() ?: return 0
        return v.toString().toShort()
    }

    override fun decodeInt(): Int {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().toInt()
        }
        val v = currentValue() ?: return 0
        return v.toString().toInt()
    }

    override fun decodeLong(): Long {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().toLong()
        }
        val v = currentValue() ?: return 0L
        return v.toString().toLong()
    }

    override fun decodeFloat(): Float {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().toFloat()
        }
        val v = currentValue() ?: return 0f
        return v.toString().toFloat()
    }

    override fun decodeDouble(): Double {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().toDouble()
        }
        val v = currentValue()?.toString()?.toDouble() ?: 0.0
        return v
    }

    override fun decodeChar(): Char {
        if (isMap && (elementIndex - 1) % 2 != 0) {
            return mapValues[(elementIndex - 1) / 2].toString().firstOrNull() ?: '\u0000'
        }
        val v = currentValue()?.toString() ?: ""
        return v.firstOrNull() ?: '\u0000'
    }

    override fun decodeString(): String {
        if (isMap) {
            val idx = elementIndex - 1
            if (idx % 2 == 0) {
                return mapKeys[idx / 2]
            } else {
                return mapValues[(idx - 1) / 2].toString()
            }
        }
        return currentValue()?.toString() ?: ""
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (currentKey == null) {
            return this
        }
        val value = currentValue() ?: return MessageDecoder(emptyJsObject())
        val kind = descriptor.kind
        val isSubMap = kind is StructureKind.MAP
        currentKey = null
        return MessageDecoder(value, isMap = isSubMap)
    }
}

inline fun <reified T> decodeFromJsObject(deserializer: DeserializationStrategy<T>, obj: JsAny): T {
    val decoder = MessageDecoder(obj)
    return deserializer.deserialize(decoder)
}

inline fun <reified T> decodeFromJsObject(obj: JsAny): T {
    val deserializer = serializer<T>()
    return decodeFromJsObject(deserializer, obj)
}
