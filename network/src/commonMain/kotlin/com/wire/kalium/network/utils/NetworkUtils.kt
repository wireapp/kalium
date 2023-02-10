/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.utils

import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess

internal fun HttpRequestBuilder.setWSSUrl(baseUrl: Url, vararg path: String) {
    url {
        host = baseUrl.host
        pathSegments = baseUrl.pathSegments + path
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

private fun HttpRequestBuilder.setHttpsUrl(baseUrl: Url, path: List<String>) {
    url {
        host = baseUrl.host
        pathSegments = baseUrl.pathSegments + path
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
internal inline fun <T : Any, U : Any> NetworkResponse<T>.mapSuccess(mapping: ((T) -> U)): NetworkResponse<U> =
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

internal inline fun <T : Any> NetworkResponse<T>.onSuccess(fn: (NetworkResponse.Success<T>) -> Unit): NetworkResponse<T> =
    this.apply { if (this is NetworkResponse.Success) fn(this) }

/**
 * Wraps exceptions thrown during the request or parsing of the response, like
 * exceptions thrown due to inability to reach the server.
 * This does **not** handle the response itself (i.e. HTTP status, body, etc.),
 * but may be used to catch exceptions thrown during those steps.
 *
 * @return [NetworkResponse.Success] if everything went smooth
 * @return [NetworkResponse.Error] with a [KaliumException.GenericError] in case of an error
 */
private inline fun <reified ResponseType : Any> handlingNetworkException(
    performRequest: () -> NetworkResponse<ResponseType>
): NetworkResponse<ResponseType> = try {
    performRequest()
} catch (e: Exception) {
    NetworkResponse.Error(KaliumException.GenericError(e))
}

/**
 * Wraps a producer of [HttpResponse] and attempts to parse the server response based on the [BodyType].
 * @return - Successful response (HTTP Status Codes from 200 to 299):
 * a [NetworkResponse.Success] with the expected [BodyType] will be returned.
 *
 * - Unsuccessful response (any other HTTP Status Code):
 * a [NetworkResponse.Error] with a [KaliumException]. Will try to read it the body as an [ErrorResponse].
 * It's possible to intercept this and do the mapping to a [NetworkResponse] through [unsuccessfulResponseOverride].
 *
 * - Exceptions failure to reach server or parse response:
 * a [NetworkResponse.Error] containing a [KaliumException.GenericError]
 *
 * @param unsuccessfulResponseOverride Allows to intercept any unsuccessful response
 * and map it to a [NetworkResponse] as needed. This block can return null in order don't override.
 * Useful when handling custom ErrorBody, for example.
 * @param performRequest the block that will result into the [HttpResponse]
 * @see KaliumException
 * @see ErrorResponse
 */
internal suspend inline fun <reified BodyType : Any> wrapKaliumResponse(
    unsuccessfulResponseOverride: (HttpResponse) -> NetworkResponse<BodyType>? = { null },
    performRequest: () -> HttpResponse
): NetworkResponse<BodyType> = handlingNetworkException {
    val result = performRequest()
    val status = result.status
    return if (status.isSuccess()) {
        NetworkResponse.Success(result.body(), result)
    } else {
        unsuccessfulResponseOverride(result) ?: handleUnsuccessfulResponse(result)
    }
}

internal suspend fun handleUnsuccessfulResponse(
    result: HttpResponse
): NetworkResponse.Error {
    val status = result.status

    val errorResponse = try {
        result.body()
    } catch (_: NoTransformationFoundException) {
        // When the backend returns something that is not a JSON for whatever reason.
        ErrorResponse(status.value, status.description, "")
    }

    val kException = when (status.value) {
        HttpStatusCode.Unauthorized.value -> {
            kaliumLogger.e("Unauthorized request, $result")
            KaliumException.Unauthorized(status.value)
        }
        in (300..399) -> KaliumException.RedirectError(errorResponse)
        in (400..499) -> KaliumException.InvalidRequestError(errorResponse)
        in (500..599) -> KaliumException.ServerError(errorResponse)
        else -> {
            kaliumLogger.e("Server responded with unsupported status code: [$status]")
            KaliumException.ServerError(errorResponse)
        }
    }
    return NetworkResponse.Error(kException)
}
