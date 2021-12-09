package com.wire.kalium.network.utils

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.statement.*
import io.ktor.util.*
import kotlinx.serialization.SerializationException

sealed class NetworkResponse<out T> {
    data class Success<out T : Any>(val response: HttpResponse, val body: T) : NetworkResponse<T>()
    data class Error<out E : KaliumException>(val kException: KaliumException) : NetworkResponse<E>()
}

fun <T> NetworkResponse<T>.successValue(): T = (this as NetworkResponse.Success).body
fun <T> NetworkResponse<T>.isSuccessful(): Boolean = this is NetworkResponse.Success
fun <T> NetworkResponse<T>.asHttpResponseCode(): Int = (this as NetworkResponse.Success).response.status.value
fun <T> NetworkResponse<T>.responseHeaders(): Map<String, List<String>> = (this as NetworkResponse.Success).response.headers.toMap()
fun <T> NetworkResponse<T>.errorValue(): KaliumException = (this as NetworkResponse.Error).kException

suspend inline fun <reified BodyType> wrapKaliumResponse(performRequest: () -> HttpResponse): NetworkResponse<BodyType> =
    try {
        val result = performRequest()
        NetworkResponse.Success(result, result.receive())
    } catch (e: ResponseException) { // ktor exception
        e.printStackTrace()
        when (e) {
            is RedirectResponseException -> {
                // 300 .. 399
                NetworkResponse.Error(kException = KaliumException.RedirectError(e.response.status.value, e))
            }
            is ClientRequestException -> {
                if (e.response.status.value == 400) {
                    // 400
                    NetworkResponse.Error(kException = KaliumException.InvalidRequestError(e.response.receive(), e))
                } else {
                    // 401 .. 499
                    NetworkResponse.Error(kException = KaliumException.ServerError(e.response.receive(), e))
                }
            }
            is ServerResponseException -> {
                // 500 .. 599
                NetworkResponse.Error(kException = KaliumException.ServerError(e.response.receive(), e))
            }
            else -> {
                NetworkResponse.Error(kException = KaliumException.GenericError(e.response.receive(), e))
            }
        }
    } catch (e: SerializationException) {
        NetworkResponse.Error(
            kException = KaliumException.GenericError(ErrorResponse(400, e.message ?: "There was a Serialization error ", e.toString()), e)
        )
    }
