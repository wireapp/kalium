package com.wire.kalium.logic.data.message

interface ProtoContentMapper {
    fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob
    fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent
}

// IDE is currently complaining that it's not implemented on JVM. The compiler builds it correctly. The IDE is wrong.
expect fun provideProtoContentMapper(): ProtoContentMapper
