package com.wire.kalium.network.utils

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
import kotlinx.serialization.SerializationException

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

internal suspend fun <T : Any, R : Any> NetworkResponse<T>.flatMap(
    fn: suspend (NetworkResponse.Success<T>) -> NetworkResponse<R>
): NetworkResponse<R> =
    if (isSuccessful()) {
        fn(this)
    } else {
        this
    }

internal fun <T : Any> NetworkResponse<T>.onFailure(fn: (NetworkResponse.Error) -> Unit): NetworkResponse<T> =
    this.apply { if (this is NetworkResponse.Error) fn(this) }

internal fun <T : Any> NetworkResponse<T>.onSuccess(fn: (NetworkResponse.Success<T>) -> Unit): NetworkResponse<T> =
    this.apply { if (this is NetworkResponse.Success) fn(this) }

internal inline fun <reified BodyType : Any> wrapKaliumException(
    performRequest: () -> NetworkResponse<BodyType>
): NetworkResponse<BodyType> = try {
    performRequest()
} catch (e: SerializationException) {
    NetworkResponse.Error(
        kException = KaliumException.GenericError(cause = e)
    )
} catch (e: Exception) {
    NetworkResponse.Error(
        kException = KaliumException.GenericError(e)
    )
}

internal suspend inline fun <reified BodyType : Any> wrapKaliumResponse(
    performRequest: () -> HttpResponse
): NetworkResponse<BodyType> = wrapKaliumException {
    val result = performRequest()
    handleResult(result)
}

private suspend inline fun <reified BodyType : Any> handleResult(result: HttpResponse): NetworkResponse<BodyType> {
    val status = result.status
    return if (status.isSuccess()) {
        NetworkResponse.Success(
            value = result.body(),
            httpResponse = result
        )
    } else {
        when (status.value) {
            HttpStatusCode.Unauthorized.value -> {
                kaliumLogger.e("Unauthorized request, $result")
                NetworkResponse.Error(KaliumException.Unauthorized(status.value))
            }
            in (300..399) -> NetworkResponse.Error(kException = KaliumException.RedirectError(result.body()))
            in (400..499) -> try {
                NetworkResponse.Error(KaliumException.InvalidRequestError(result.body()))
            } catch (e: NoTransformationFoundException) {
                NetworkResponse.Error(KaliumException.GenericError(e))
            }
            in (500..599) -> NetworkResponse.Error(kException = KaliumException.ServerError(result.body()))
            else -> {
                kaliumLogger.e("Server responded with unsupported status code: [$status]")
                NetworkResponse.Error(kException = KaliumException.ServerError(result.body()))
            }
        }
    }
}
