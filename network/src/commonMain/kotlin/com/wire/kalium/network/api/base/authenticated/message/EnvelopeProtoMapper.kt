package com.wire.kalium.network.api.base.authenticated.message

interface EnvelopeProtoMapper {
    fun encodeToProtobuf(envelopeParameters: MessageApi.Parameters.QualifiedDefaultParameters): ByteArray
}

expect fun provideEnvelopeProtoMapper(): EnvelopeProtoMapper
