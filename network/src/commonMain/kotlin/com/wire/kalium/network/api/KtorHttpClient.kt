package com.wire.kalium.network.api

import com.wire.kalium.network.tools.HostProvider
import com.wire.kalium.network.tools.KtxSerializer
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
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.URLProtocol

class KtorHttpClient(
    private val hostProvider: HostProvider,
    private val engine: HttpClientEngine,
    private val authenticationManager: AuthenticationManager,
) {

    val provideWebSocketClient by lazy {
        HttpClient(engine) {
            defaultRequest {
                host = hostProvider.host
                url.protocol = URLProtocol.WSS
            }
            install(WebSockets)
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
        }
    }

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
                serializer = KotlinxSerializer(KtxSerializer.json)
                accept(ContentType.Application.Json)
            }
        }
    }
}

class KaliumKtorResult<BodyType : Any>(private val httpResponse: HttpResponse, private val body: BodyType) : KaliumHttpResult<BodyType> {
    override val httpStatusCode: Int
        get() = httpResponse.status.value
    override val headers: Headers
        get() = httpResponse.headers
    override val resultBody: BodyType
        get() = body
}

suspend inline fun <reified BodyType : Any> wrapKaliumResponse(performRequest: () -> HttpResponse): KaliumHttpResult<BodyType> {
    try {
        val result = performRequest()
        return KaliumKtorResult(result, result.receive())
    } catch (e: ResponseException) {
        when (e) {
            is RedirectResponseException -> {
                // 300..399
                throw e
            }
            is ClientRequestException -> {
                // 400..499
                throw e
            }
            is ServerResponseException -> {
                // 500..599
                throw e
            }
            else -> {
                // other ResponseException
                throw e
            }
        }
    }
}

fun HttpResponse.isSuccessful(): Boolean = this.status.value in 200..299
