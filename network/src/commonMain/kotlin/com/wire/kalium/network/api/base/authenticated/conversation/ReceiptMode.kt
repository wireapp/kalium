package com.wire.kalium.network.api.base.authenticated.conversation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = ReceiptMode.ReceiptModeAsIntSerializer::class)
enum class ReceiptMode(val value: Int) {
    DISABLED(0),
    ENABLED(1);

    object ReceiptModeAsIntSerializer : KSerializer<ReceiptMode> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReceiptMode", PrimitiveKind.INT).nullable

        override fun serialize(encoder: Encoder, value: ReceiptMode) {
            encoder.encodeInt(value.value)
        }

        @OptIn(ExperimentalSerializationApi::class)
        override fun deserialize(decoder: Decoder): ReceiptMode {
            val value = if (decoder.decodeNotNullMark()) decoder.decodeInt() else 0
            return if (value > 0) ENABLED else DISABLED
        }
    }
}
