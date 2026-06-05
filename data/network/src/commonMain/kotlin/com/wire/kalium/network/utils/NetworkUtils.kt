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
@file:Suppress("TooManyFunctions")

package com.wire.kalium.network.utils

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

internal fun HttpRequestBuilder.setWSSUrl(baseUrl: Url, vararg path: String) {
    url {
        host = baseUrl.host
        pathSegments = baseUrl.rawSegments + path
        protocol = URLProtocol.WSS
        port = URLProtocol.WSS.defaultPort
    }
}

internal fun HttpRequestBuilder.setUrl(baseUrl: Url, vararg path: String) {
    setHttpsUrl(baseUrl, path.toList())
}

internal fun HttpRequestBuilder.setUrl(baseUrl: String, vararg path: String) {
    val parsedUrl = Url(baseUrl)
    setHttpsUrl(parsedUrl, path.toList())
}

internal fun HttpRequestBuilder.setUrl(baseUrl: String, path: List<String>) {
    val parsedUrl = Url(baseUrl)
    setHttpsUrl(parsedUrl, path)
}

private fun HttpRequestBuilder.setHttpsUrl(baseUrl: Url, path: List<String>) {
    url {
        host = baseUrl.host
        pathSegments = baseUrl.segments + path
        protocol = URLProtocol.HTTPS
    }
}

internal fun String.splitSetCookieHeader(): List<String> {
    var comma = indexOf(',')

    if (comma == -1) {
        return listOf(this)
    }

    val result = mutableListOf<String>()
    var current = 0

    var equals = indexOf('=', comma)
    var semicolon = indexOf(';', comma)
    while (current < length && comma > 0) {
        if (equals < comma) {
            equals = indexOf('=', comma)
        }

        var nextComma = indexOf(',', comma + 1)
        while (nextComma >= 0 && nextComma < equals) {
            comma = nextComma
            nextComma = indexOf(',', nextComma + 1)
        }

        if (semicolon < comma) {
            semicolon = indexOf(';', comma)
        }

        // No more keys remaining.
        if (equals < 0) {
            result += substring(current)
            return result
        }

        // No ';' between ',' and '=' => We're on a header border.
        if (semicolon == -1 || semicolon > equals) {
            result += substring(current, comma)
            current = comma + 1
            // Update comma index at the end of loop.
        }

        // ',' in value, skip it and find next.
        comma = nextComma
    }

    // Add last chunk if no more ',' available.
    if (current < length) {
        result += substring(current)
    }

    return result
}

/**
 * If the request is successful, perform [mapping] and create a new Success with its result
 * @return A new [NetworkResponse.Success] with the mapped result,
 * or [NetworkResponse.Error] if it was never a success to begin with
 */
inline fun <T : Any, U : Any> NetworkResponse<T>.mapSuccess(mapping: ((T) -> U)): NetworkResponse<U> =
    if (isSuccessful()) {
        NetworkResponse.Success(mapping(this.value), this.headers, this.httpCode)
    } else {
        this
    }

internal inline fun <T : Any, R : Any> NetworkResponse<T>.flatMap(
    fn: (NetworkResponse.Success<T>) -> NetworkResponse<R>
): NetworkResponse<R> =
    if (isSuccessful()) {
        fn(this)
    } else {
        this
    }

internal inline fun <T : Any> NetworkResponse<T>.onFailure(fn: (NetworkResponse.Error) -> Unit): NetworkResponse<T> =
    this.apply { if (this is NetworkResponse.Error) fn(this) }

internal inline fun <T : Any> NetworkResponse<T>.onSuccess(
    fn: (NetworkResponse.Success<T>) -> Unit
): NetworkResponse<T> =
    this.apply { if (this is NetworkResponse.Success) fn(this) }
