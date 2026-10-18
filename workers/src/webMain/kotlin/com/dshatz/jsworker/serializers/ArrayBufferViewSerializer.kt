package com.dshatz.jsworker.serializers

import com.dshatz.jsworker.MessageDecoder
import com.dshatz.jsworker.MessageEncoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.khronos.webgl.ArrayBufferView
import org.khronos.webgl.Int8Array
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