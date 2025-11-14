/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.network.api.model.FederationErrorResponse
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.FederationError
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.kaliumLogger
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

internal suspend inline fun <reified ResponseType : Any> wrapRequest(
    customErrorInterceptor: ErrorResponseInterceptor<ResponseType>? = null,
    json: Json = KtxSerializer.json,
    performRequest: () -> HttpResponse,
): NetworkResponse<ResponseType> = wrapRequest(
    customErrorInterceptor = customErrorInterceptor,
    successHandler = { response ->
        NetworkResponse.Success(
            response.body<ResponseType>(),
            response
        )
    },
    json = json,
    performRequest = performRequest,
)

/**
 * Handles an HTTP response with an error status, transforming it into a corresponding NetworkResponse.
 *
 * @param performRequest An HTTP response producer
 * @param customErrorInterceptor An optional interceptor for custom error responses.
 * This interceptor will be executed **after** more critical interceptors, like [UnauthorizedResponseInterceptor] and
 * [FederationErrorResponseInterceptor], but **before** the [BaseErrorResponseInterceptor].
 * @param successHandler A handler for successful HTTP responses (status codes 200..299).
 * By default, it will deserialize the body to the wanted [ResponseType].
 * @return A NetworkResponse representing the error, which may either include specific error details or a generic error object.
 */
internal suspend inline fun <reified ResponseType : Any> wrapRequest(
    customErrorInterceptor: ErrorResponseInterceptor<ResponseType>? = null,
    successHandler: suspend (HttpResponse) -> NetworkResponse<ResponseType>,
    json: Json = KtxSerializer.json,
    performRequest: () -> HttpResponse,
): NetworkResponse<ResponseType> = handlingNetworkException {
    val result = performRequest()
    if (result.status.isSuccess()) {
        successHandler(result)
    } else {
        val responseData = HttpResponseData(
            headers = result.headers,
            statusCode = result.status,
            body = result.bodyAsText(),
            json = json,
        )

        UnauthorizedResponseInterceptor.intercept(responseData)
            ?: FederationErrorResponseInterceptor.intercept(responseData)
            ?: MLSErrorResponseHandler.intercept(responseData)
            ?: customErrorInterceptor?.intercept(responseData)
            ?: BaseErrorResponseInterceptor.intercept(responseData)
    }
}

/**
 * Wraps exceptions thrown during the request or parsing of the response, like
 * exceptions thrown due to inability to reach the server.
 * This does **not** handle the response itself (i.e. HTTP status, body, etc.),
 * but may be used to catch exceptions thrown during those steps.
 *
 * @return [NetworkResponse.Success] if everything went smooth
 * @return [NetworkResponse.Error] with a [KaliumException.GenericError] in case of an error
 */
@Suppress("TooGenericExceptionCaught")
private inline fun <reified ResponseType : Any> handlingNetworkException(
    performRequest: () -> NetworkResponse<ResponseType>
): NetworkResponse<ResponseType> = try {
    performRequest()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    NetworkResponse.Error(KaliumException.GenericError(e))
}

internal fun interface ErrorResponseInterceptor<out ResponseType : Any> {

    /**
     * Intercepts the provided HTTP response to determine if it can be mapped to a specific `NetworkResponse`.
     *
     * @param ResponseType the type of the successful response body expected.
     * @param httpResponseData the HTTP response to be intercepted, containing headers, status code, and body.
     * @return a specific `NetworkResponse` instance if the response can be mapped, or null if it cannot.
     */
    suspend fun intercept(httpResponseData: HttpResponseData): NetworkResponse<ResponseType>?
}

/**
 * Checks whether the HTTP response has a status code of 401 (Unauthorized).
 * If the response status code matches [HttpStatusCode.Unauthorized], it maps the response to a [NetworkResponse.Error]
 * containing an [KaliumException.Unauthorized] exception with the HTTP status code as its error code.
 *
 * @see ErrorResponseInterceptor
 * @see NetworkResponse.Error
 * @see KaliumException.Unauthorized
 */
internal object UnauthorizedResponseInterceptor : ErrorResponseInterceptor<Unit> {
    override suspend fun intercept(
        httpResponseData: HttpResponseData
    ): NetworkResponse.Error? = if (httpResponseData.status == HttpStatusCode.Unauthorized) {
        NetworkResponse.Error(KaliumException.Unauthorized(httpResponseData.status.value))
    } else {
        null
    }
}

/**
 * A base implementation of the [ErrorResponseInterceptor] interface used for intercepting HTTP responses.
 * This will only return [NetworkResponse.Error], never null. And will attempt to map whatever response body
 * to a [GenericAPIErrorResponse] object, the "default" error type when dealing with Wire API.
 */
internal object BaseErrorResponseInterceptor : ErrorResponseInterceptor<Any> {
    override suspend fun intercept(httpResponseData: HttpResponseData): NetworkResponse.Error {
        val errorResponse = try {
            httpResponseData.parseBody<GenericAPIErrorResponse>()
        } catch (_: IllegalArgumentException) {
            GenericAPIErrorResponse(
                code = httpResponseData.status.value,
                label = httpResponseData.status.description,
                message = httpResponseData.body
            )
        }

        @Suppress("MagicNumber")
        val kException = when (httpResponseData.status.value) {
            in (300..399) -> KaliumException.RedirectError(errorResponse)
            in (400..499) -> KaliumException.InvalidRequestError(errorResponse)
            in (500..599) -> KaliumException.ServerError(errorResponse)
            else -> {
                kaliumLogger.e("Server responded with unsupported status code: [${httpResponseData.status}]")
                KaliumException.ServerError(errorResponse)
            }
        }
        return NetworkResponse.Error(kException)
    }
}

/**
 * An interceptor responsible for handling federation-related error responses in an HTTP call.
 *
 * The following response types are specifically evaluated:
 * - [FederationErrorResponse.Generic] federation errors identified by the `federation` type or label in the response body
 * - [FederationErrorResponse.Conflict] errors with `non_federating_backends` (HTTP Status: [HttpStatusCode.Conflict])
 * - [FederationErrorResponse.Unreachable] errors with `unreachable_backends`
 * (HTTP Status: [HttpStatusCode.Companion.UnreachableRemoteBackends])
 *
 */
internal object FederationErrorResponseInterceptor : ErrorResponseInterceptor<Any> {

    override suspend fun intercept(httpResponseData: HttpResponseData): NetworkResponse.Error? {
        val genericFederationResponse = try {
            // Attempts to parse the generic federation error, to check for labels and data.type
            httpResponseData.parseBody<FederationErrorResponse.Generic>()
        } catch (e: IllegalArgumentException) {
            null
        }

        val isGenericFederationError = genericFederationResponse?.cause?.type == FEDERATION_ERROR_TYPE ||
                genericFederationResponse?.label?.contains(FEDERATION_ERROR_TYPE) == true
        if (isGenericFederationError) {
            return NetworkResponse.Error(FederationError(genericFederationResponse))
        }

        // If the error doesn't match the generic federation error response,
        // check for the special cases that follow different JSON schemas
        return when (httpResponseData.status.value) {
            HttpStatusCode.Conflict.value -> {
                try {
                    val errorResponse = httpResponseData.parseBody<FederationErrorResponse.Conflict>()
                    NetworkResponse.Error(FederationError(errorResponse))
                } catch (_: IllegalArgumentException) {
                    null
                }
            }

            HttpStatusCode.UnreachableRemoteBackends.value -> {
                val errorResponse = try {
                    httpResponseData.parseBody<FederationErrorResponse.Unreachable>()
                } catch (_: NoTransformationFoundException) {
                    FederationErrorResponse.Unreachable(emptyList())
                }
                NetworkResponse.Error(FederationError(errorResponse))
            }

            else -> null
        }
    }

    private const val FEDERATION_ERROR_TYPE = "federation"

    /**
     * Custom [HttpStatusCode] to handle when one or more federated remote servers are unreachable.
     */
    @Suppress("MagicNumber")
    val HttpStatusCode.Companion.UnreachableRemoteBackends: HttpStatusCode
        get() = HttpStatusCode(533, "Unreachable remote backends")
}
