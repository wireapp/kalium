package com.wire.kalium.api

import com.wire.kalium.tools.HostProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.HttpClientEngine
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
import io.ktor.http.Headers
import io.ktor.http.URLProtocol
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

class KaliumKtorResult<BodyType : Any>(private val httpResponse: HttpResponse, private val body: BodyType) : KaliumHttpResult<BodyType> {
    override val httpStatusCode: Int
        get() = httpResponse.status.value
    override val headers: Headers
        get() = httpResponse.headers
    override val resultBody: BodyType
        get() = body
}

suspend inline fun <reified BodyType : Any> wrapKaliumResponse(performRequest: () -> HttpResponse): KaliumHttpResult<BodyType> {
    val result = performRequest()
    return KaliumKtorResult(result, result.receive())
}

fun HttpResponse.isSuccessful(): Boolean = this.status.value in 200..299
