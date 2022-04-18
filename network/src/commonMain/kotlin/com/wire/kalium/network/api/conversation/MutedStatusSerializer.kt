package com.wire.kalium.network.api.conversation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(MutedStatus::class)
class MutedStatusSerializer : KSerializer<MutedStatus?> {
    override val descriptor = PrimitiveSerialDescriptor("otr_muted_status", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: MutedStatus?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value.ordinal)
        }

    }

    override fun deserialize(decoder: Decoder): MutedStatus? {
        val rawValue = decoder.decodeInt()
        return MutedStatus.fromOrdinal(rawValue)
    }
}
