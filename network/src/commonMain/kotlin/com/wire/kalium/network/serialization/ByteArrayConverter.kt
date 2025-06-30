/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.serialization

import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.readAvailable

/**
 * A ContentConverter which does nothing, it simply passes byte arrays through as they are. This is useful
 * if you want to register your own custom binary content type with the ContentNegotiation plugin.
 */
class ByteArrayConverter : ContentConverter {

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        // Read all bytes from the channel into a ByteArray
        val buffer = mutableListOf<Byte>()
        while (!content.isClosedForRead) {
            val bytes = ByteArray(8192)
            val bytesRead = content.readAvailable(bytes)
            if (bytesRead > 0) {
                buffer.addAll(bytes.take(bytesRead))
            }
        }
        return buffer.toByteArray()
    }

    override suspend fun serialize(contentType: ContentType, charset: Charset, typeInfo: TypeInfo, value: Any?): OutgoingContent? {
        return if (value != null) ByteArrayContent(value as ByteArray, contentType) else null
    }
}

public val ContentType.Message.Mls: ContentType
    get() = ContentType("message", "mls")
public val ContentType.Application.JoseJson: ContentType
    get() = ContentType("application", "jose+json")

public val ContentType.Application.XProtoBuf: ContentType
    get() = ContentType("application", "x-protobuf")

public fun Configuration.mls(contentType: ContentType = ContentType.Message.Mls) {
    register(contentType, ByteArrayConverter())
}

public fun Configuration.xprotobuf(contentType: ContentType = ContentType.Application.XProtoBuf) {
    register(contentType, ByteArrayConverter())
}
