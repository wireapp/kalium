package com.wire.kalium.network.serialization

import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset

/**
 * A ContentConverter which does nothing, it simply passes byte arrays through as they are. This is useful
 * if you want to register your own custom binary content type with the ContentNegotiation plugin.
 */
class ByteArrayConverter: ContentConverter {

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        return content.toByteArray()
    }

    override suspend fun serialize(contentType: ContentType, charset: Charset, typeInfo: TypeInfo, value: Any): OutgoingContent? {
        return ByteArrayContent(value as ByteArray, contentType)
    }

}

public val ContentType.Message.Mls: ContentType
    get() = ContentType("message", "mls")

public val ContentType.Application.XProtoBuf: ContentType
    get() = ContentType("application", "x-protobuf")

public fun Configuration.mls(contentType: ContentType = ContentType.Message.Mls) {
    register(contentType, ByteArrayConverter())
}

public fun Configuration.xprotobuf(contentType: ContentType = ContentType.Application.XProtoBuf) {
    register(contentType, ByteArrayConverter())
}
