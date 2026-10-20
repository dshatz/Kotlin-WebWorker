@file:OptIn(ExperimentalWasmJsInterop::class)

package com.dshatz.jsworker.serializers

import com.dshatz.jsworker.emptyJsObject
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
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.toJsString

@OptIn(ExperimentalSerializationApi::class)
class MessageDecoder(private val sourceObj: JsAny, private val isMap: Boolean = false) : AbstractDecoder() {
    override val serializersModule: SerializersModule = jsSerialModule

    private val map: Map<String, JsAny> = sourceObj.asJsObject()
    private val mapKeys: List<String> = map.keys.toList()
    private val mapValues: List<JsAny> = map.values.toList()
    private var elementIndex = 0
    private var currentKey: String? = null

    private fun currentElement(): JsAny? {
        val idx = elementIndex - 1
        return if (isMap) {
            if (idx % 2 == 0) mapKeys[idx / 2].toJsString()
            else mapValues[idx / 2]
        } else {
            val key = currentKey ?: return null
            map[key] ?: getPropertyValue(sourceObj, key)
        }
    }

    private fun <T> decodePrimitive(convert: (JsAny) -> T): T {
        val element = currentElement() ?: error("Value for key '$currentKey' is null or missing")
        return convert(element)
    }

    internal fun currentValue(): JsAny? {
        val key = currentKey ?: return null
        return map[key] ?: getPropertyValue(sourceObj, key)
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val kind = descriptor.kind
        return when {
            kind is StructureKind.LIST -> {
                val lengthVal = getPropertyValue(sourceObj, "length")
                val length = lengthVal?.toString()?.toInt() ?: map.size
                if (elementIndex < length) {
                    currentKey = elementIndex.toString()
                    elementIndex++
                } else {
                    CompositeDecoder.DECODE_DONE
                }
            }
            isMap -> {
                val totalElements = map.size * 2
                if (elementIndex < totalElements) {
                    elementIndex++
                } else {
                    CompositeDecoder.DECODE_DONE
                }
            }
            else -> {
                if (elementIndex < descriptor.elementsCount) {
                    val idx = elementIndex++
                    currentKey = descriptor.getElementName(idx)
                    idx
                } else {
                    CompositeDecoder.DECODE_DONE
                }
            }
        }
    }

    override fun decodeValue(): Any {
        return currentElement() ?: error("Value for key '$currentKey' is null or missing")
    }

    override fun decodeNotNullMark(): Boolean {
        return currentElement() != null
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeBoolean(): Boolean = decodePrimitive { it.toString().toBoolean() }
    override fun decodeByte(): Byte = decodePrimitive { it.toString().toByte() }
    override fun decodeShort(): Short = decodePrimitive { it.toString().toShort() }
    override fun decodeInt(): Int = decodePrimitive { it.toString().toInt() }
    override fun decodeLong(): Long = decodePrimitive { it.toString().toLong() }
    override fun decodeFloat(): Float = decodePrimitive { it.toString().toFloat() }
    override fun decodeDouble(): Double = decodePrimitive { it.toString().toDouble() }
    override fun decodeChar(): Char = decodePrimitive { it.toString().firstOrNull() ?: '\u0000' }
    override fun decodeString(): String = decodePrimitive { it.toString() }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (currentKey == null && !isMap) {
            return this
        }
        val value = currentElement() ?: return MessageDecoder(emptyJsObject())
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
