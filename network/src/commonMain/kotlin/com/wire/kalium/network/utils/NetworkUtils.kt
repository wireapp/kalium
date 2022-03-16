package com.wire.kalium.network.utils

import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException


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
inline fun <T : Any, U : Any> NetworkResponse<T>. mapSuccess(mapping: ((T) -> U)): NetworkResponse<U> =
    if (isSuccessful()) {
        NetworkResponse.Success(mapping(this.value), this.headers, this.httpCode)
    } else {
        NetworkResponse.Error(kException)
    }

fun <E : KaliumException> NetworkResponse<E>.onFailure(fn: (NetworkResponse.Error<E>) -> Unit): NetworkResponse<E> =
    this.apply { if (this is NetworkResponse.Error) fn(this) }


fun <T : Any> NetworkResponse<T>.onSuccess(fn: (NetworkResponse.Success<T>) -> Unit): NetworkResponse<T> =
    this.apply { if (this is NetworkResponse.Success) fn(this) }

internal suspend inline fun <reified BodyType : Any> wrapKaliumResponse(performRequest: () -> HttpResponse): NetworkResponse<BodyType> =
    try {
        val result = performRequest()
        NetworkResponse.Success(
            value = result.body(),
            httpResponse = result
        )
    } catch (e: ResponseException) { // ktor exception
        when (e) {
            is RedirectResponseException -> {
                // 300 .. 399
                NetworkResponse.Error(kException = KaliumException.RedirectError(e.response.body()))
            }
            is ClientRequestException -> {
                // 400 .. 499
                when(e.response.status) {
                    // TODO: log if 401 got to this step, since it need to be handled by the http client
                    // for 401 error the BE return response with content-type: text/html which our ktor client
                    // has no idea how to parse -> app crash
                    HttpStatusCode.Unauthorized -> NetworkResponse.Error(KaliumException.Unauthorized(e.response.status.value))

                    // TODO: try catch the parsing of error body
                    else -> NetworkResponse.Error(kException = KaliumException.InvalidRequestError(e.response.body()))
                }            }
            is ServerResponseException -> {
                // 500 .. 599
                // TODO: do 500 errors have body
                NetworkResponse.Error(kException = KaliumException.ServerError(e.response.body()))
            }
            else -> {
                NetworkResponse.Error(kException = KaliumException.GenericError(e))
            }
        }
    } catch (e: SerializationException) {
        NetworkResponse.Error(
            kException = KaliumException.GenericError(cause = e)
        )
    } catch (e: IOException) {
        // TODO: does Ktor throw IOException on time-out/no-internet connection
        NetworkResponse.Error(
            kException = KaliumException.NetworkUnavailableError(cause = e)
        )

    } catch (e: Exception) {
        // TODO: should we catch 'Em all
        NetworkResponse.Error(
            kException = KaliumException.GenericError(e)
        )
    }
