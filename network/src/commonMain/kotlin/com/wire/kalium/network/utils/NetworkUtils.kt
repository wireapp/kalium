package com.wire.kalium.network.utils

import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.client.call.receive
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.RedirectResponseException
import io.ktor.client.features.ResponseException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.setCookie
import io.ktor.util.toMap
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class NetworkResponse<out T> {
    data class Success<out T : Any>(internal val response: HttpResponse, val value: T) : NetworkResponse<T>()
    data class Error<out E : KaliumException>(val kException: KaliumException) : NetworkResponse<E>()
}

fun <T> NetworkResponse<T>.httpResponseCode(): Int = if (isSuccessful()) this.response.status.value else kException.errorCode
fun <T> NetworkResponse<T>.httpResponseHeaders(): Map<String, String?> =
    (this as NetworkResponse.Success).response
        .headers.toMap().mapValues { headerEntry -> headerEntry.value.firstOrNull() } //Ignore header duplication on purpose

fun <T> NetworkResponse<T>.httpResponseCookies(): Map<String, String> =
    (this as NetworkResponse.Success).response
        .setCookie().associate {
            it.name to it.value
        }

@OptIn(ExperimentalContracts::class)
fun <T> NetworkResponse<T>.isSuccessful(): Boolean {
    contract {
        returns(true) implies (this@isSuccessful is NetworkResponse.Success)
        returns(false) implies (this@isSuccessful is NetworkResponse.Error)
    }
    return this@isSuccessful is NetworkResponse.Success
}

suspend inline fun <reified BodyType> wrapKaliumResponse(performRequest: () -> HttpResponse): NetworkResponse<BodyType> =
    try {
        val result = performRequest()
        NetworkResponse.Success(result, result.receive())
    } catch (e: ResponseException) { // ktor exception
        e.printStackTrace()
        when (e) {
            is RedirectResponseException -> {
                // 300 .. 399
                NetworkResponse.Error(kException = KaliumException.RedirectError(e.response.receive()))
            }
            is ClientRequestException -> {
                // 400 .. 499
                NetworkResponse.Error(kException = KaliumException.InvalidRequestError(e.response.receive()))
            }
            is ServerResponseException -> {
                // 500 .. 599
                NetworkResponse.Error(kException = KaliumException.ServerError(e.response.receive()))
            }
            else -> {
                NetworkResponse.Error(kException = KaliumException.GenericError(e.response.receive(), e))
            }
        }
    } catch (e: SerializationException) {
        NetworkResponse.Error(
            kException = KaliumException.GenericError(ErrorResponse(400, e.message ?: "There was a Serialization error ", e.toString()), e)
        )
    } catch (e: IOException) {
        NetworkResponse.Error(
            kException = KaliumException.NetworkUnavailableError(
                ErrorResponse(
                    400,
                    e.message ?: "There was an I/O. Check the internet connection? ",
                    e.toString()
                ), e
            )
        )
    } catch (e: Exception) {
        NetworkResponse.Error(
            kException = KaliumException.GenericError(ErrorResponse(400, e.message ?: "There was a General error :( ", e.toString()), e)
        )
    }
