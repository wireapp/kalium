package com.wire.kalium.api

import com.sun.xml.internal.ws.wsdl.writer.document.soap12.BodyType
import com.wire.kalium.exceptions.ApiErrorException
import com.wire.kalium.exceptions.InvalidRequestException
import com.wire.kalium.exceptions.OtherException
import com.wire.kalium.tools.HostProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.RedirectResponseException
import io.ktor.client.features.ResponseException
import io.ktor.client.features.ServerResponseException
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.BearerTokens
import io.ktor.client.features.auth.providers.bearer
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class KtorHttpClient(
        private val hostProvider: HostProvider,
        private val engine: HttpClientEngine,
        private val authenticationManager: AuthenticationManager,
) {

    val provideKtorHttpClient by lazy {
        HttpClient(engine) {
            defaultRequest {
                header("Content-Type", "application/json")
                host = hostProvider.host
                url.protocol = URLProtocol.HTTPS
            }
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(
                                accessToken = authenticationManager.accessToken(),
                                refreshToken = authenticationManager.refreshToken()
                        )
                    }
                    refreshTokens { unauthorizedResponse: HttpResponse ->
                        TODO("refresh the tokens, interface?")
                    }
                }
            }
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
                accept(ContentType.Application.Json)
            }
        }
    }
}

class KaliumKtorResult<out BodyType : Any, out ErrorType>(private val httpResponse: HttpResponse, private val body: BodyType) : KaliumHttpResult<BodyType> {
    override val httpStatusCode: Int
        get() = httpResponse.status.value
    override val headers: Set<Map.Entry<String, List<String>>>
        get() = httpResponse.headers.entries()
    override val resultBody: BodyType
        get() = body
}

sealed class NetworkResponse<out T : Any, out E : Any> {
    data class Success<out T : Any>(val code: Int, val body: T) : NetworkResponse<T, Nothing>()
    data class ServerError<out E : Any>(val code: Int, val body: E) : NetworkResponse<Nothing, E>()
    object InvalidRequest : NetworkResponse<Nothing, Nothing>()
    data class GenericError(val throwable: Throwable) : NetworkResponse<Nothing, Nothing>()
}

suspend inline fun <reified BodyType : Any, reified ErrorType : Any> wrapKaliumResponse(performRequest: () -> HttpResponse): NetworkResponse<BodyType, ErrorType> {
    try {
        val result = performRequest()
        return NetworkResponse.Success(result.status.value, result.receive())
    } catch (e: ResponseException) { // ktor exception
        when (e) {
            is RedirectResponseException -> {
                // 300 .. 399
                return NetworkResponse.GenericError(e)
            }
            is ClientRequestException -> {
                return if (e.response.status.value == 400) {
                    // 400
                    NetworkResponse.InvalidRequest
                } else {
                    // 401 .. 499
                    NetworkResponse.ServerError(code = e.response.status.value, body = e.response.receive())
                }
            }
            is ServerResponseException -> {
                // 500 .. 599
                return NetworkResponse.GenericError(e)
            }
            else -> {
                return NetworkResponse.GenericError(e)
            }
        }
    } catch (e: SerializationException) {
        return NetworkResponse.GenericError(e)
    }
}

fun HttpResponse.isSuccessful(): Boolean = this.status.value in 200..299
