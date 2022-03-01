package com.wire.kalium.network.api.message

interface EnvelopeProtoMapper {
    fun encodeToProtobuf(envelopeParameters: MessageApi.Parameters.QualifiedDefaultParameters): ByteArray
}

expect fun provideEnvelopeProtoMapper(): EnvelopeProtoMapper
