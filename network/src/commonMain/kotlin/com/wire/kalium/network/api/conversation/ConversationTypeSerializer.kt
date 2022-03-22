package com.wire.kalium.network.api.conversation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializer(ConversationResponse.Type::class)
class ConversationTypeSerializer : KSerializer<ConversationResponse.Type> {
    override val descriptor = PrimitiveSerialDescriptor("type", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: ConversationResponse.Type) = encoder.encodeInt(value.id)

    override fun deserialize(decoder: Decoder): ConversationResponse.Type {
        val rawValue = decoder.decodeInt()
        return ConversationResponse.Type.fromId(rawValue)
    }
}
