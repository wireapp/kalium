package com.wire.kalium.network.api.base.authenticated.conversation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ReceiptMode.ReceiptModeAsIntSerializer::class)
enum class ReceiptMode(val value: Int) {
    DISABLED(0),
    ENABLED(1);

    object ReceiptModeAsIntSerializer : KSerializer<ReceiptMode> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReceiptMode", PrimitiveKind.INT)
        override fun serialize(encoder: Encoder, value: ReceiptMode) {
            encoder.encodeInt(value.value)
        }

        override fun deserialize(decoder: Decoder): ReceiptMode {
            val value = decoder.decodeInt()
            return if (value > 0) ENABLED else DISABLED
        }
    }
}
