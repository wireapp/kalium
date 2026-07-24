/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.cells.data

import io.ktor.http.Url
import io.ktor.http.hostWithPortIfSpecified
import io.ktor.http.protocolWithAuthority

internal data class S3QueryParameter(
    val name: String,
    val value: String,
)

internal data class S3Url(
    val url: String,
    val canonicalUri: String,
    val canonicalQueryString: String,
    val hostHeader: String,
)

internal object S3UrlBuilder {
    fun build(
        endpoint: String,
        bucket: String,
        objectKey: String,
        queryParameters: List<S3QueryParameter> = emptyList(),
    ): S3Url {
        val endpointUrl = Url(endpoint)
        val basePath = endpointUrl.encodedPath.trimEnd('/').takeUnless { it.isBlank() } ?: ""
        val canonicalUri = basePath + "/" + awsUriEncode(bucket, encodeSlash = true) +
                "/" + awsUriEncode(objectKey, encodeSlash = false)
        val baseUrl = endpointUrl.protocolWithAuthority + canonicalUri
        return S3Url(
            url = buildUrlString(baseUrl, queryParameters),
            canonicalUri = canonicalUri,
            canonicalQueryString = queryParameters.toCanonicalQueryString(),
            hostHeader = endpointUrl.hostWithPortIfSpecified,
        )
    }
}

internal fun List<S3QueryParameter>.toCanonicalQueryString(): String =
    map { awsUriEncode(it.name, encodeSlash = true) to awsUriEncode(it.value, encodeSlash = true) }
        .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
        .joinToString("&") { (name, value) -> "$name=$value" }

private fun buildUrlString(baseUrl: String, queryParameters: List<S3QueryParameter>): String {
    val queryString = queryParameters.toCanonicalQueryString()
    return if (queryString.isEmpty()) baseUrl else "$baseUrl?$queryString"
}

internal fun awsUriEncode(value: String, encodeSlash: Boolean): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val intValue = byte.toInt() and BYTE_MASK
        val character = intValue.toChar()
        when {
            character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' ||
                    character == '-' || character == '_' || character == '.' || character == '~' -> append(character)
            character == '/' && !encodeSlash -> append(character)
            else -> {
                append('%')
                append(HEX[intValue shr NIBBLE_SIZE])
                append(HEX[intValue and NIBBLE_MASK])
            }
        }
    }
}

private const val BYTE_MASK = 0xff
private const val NIBBLE_MASK = 0x0f
private const val NIBBLE_SIZE = 4
private val HEX = "0123456789ABCDEF".toCharArray()
