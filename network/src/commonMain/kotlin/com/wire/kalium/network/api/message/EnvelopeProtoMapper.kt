package com.wire.kalium.network.api.message

interface EnvelopeProtoMapper {
    fun encodeToProtobuf(envelopeParameters: MessageApi.Parameters.QualifiedDefaultParameters): ByteArray
}

// IDE complains it is not implemented. IDE is wrong!!
expect class EnvelopeProtoMapperImpl: EnvelopeProtoMapper
