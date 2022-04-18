package com.wire.kalium.network.api.conversation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(MemberUpdateDTO.MutedStatus::class)
class MutedStatusSerializer : KSerializer<MemberUpdateDTO.MutedStatus?> {
    override val descriptor = PrimitiveSerialDescriptor("otr_muted_status", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: MemberUpdateDTO.MutedStatus?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value.ordinal)
        }

    }

    override fun deserialize(decoder: Decoder): MemberUpdateDTO.MutedStatus? {
        val rawValue = decoder.decodeInt()
        return MemberUpdateDTO.MutedStatus.fromOrdinal(rawValue)
    }
}
