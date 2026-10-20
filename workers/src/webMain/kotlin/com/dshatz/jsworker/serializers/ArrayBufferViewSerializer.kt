package com.dshatz.jsworker.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.khronos.webgl.ArrayBufferView
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Float64Array
import org.khronos.webgl.Int16Array
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint16Array
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.Uint8ClampedArray
import kotlin.js.unsafeCast

open class ArrayBufferViewSerializer<T: ArrayBufferView>(): KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ArrayBufferView", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: T
    ) {
        if (encoder is MessageEncoder) {
            encoder.encodeValue(value)
        } else {
            error("ArrayBufferViewSerializer cannot be used without MessageEncoder")
        }
    }

    override fun deserialize(decoder: Decoder): T {
        val buffer = (decoder as MessageDecoder).currentValue()!!.unsafeCast<T>()
        return buffer
    }
}

object Int8ArraySerializer: ArrayBufferViewSerializer<Int8Array>()
object UInt8ArraySerializer: ArrayBufferViewSerializer<Uint8Array>()
object Uint8ClampedArraySerializer: ArrayBufferViewSerializer<Uint8ClampedArray>()
object Int16ArraySerializer: ArrayBufferViewSerializer<Int16Array>()
object UInt16ArraySerializer: ArrayBufferViewSerializer<Uint16Array>()
object Int32ArraySerializer: ArrayBufferViewSerializer<Int32Array>()
object UInt32ArraySerializer: ArrayBufferViewSerializer<Uint32Array>()

object Float32ArraySerializer: ArrayBufferViewSerializer<Float32Array>()
object Float64ArraySerializer: ArrayBufferViewSerializer<Float64Array>()