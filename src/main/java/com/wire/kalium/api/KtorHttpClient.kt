package com.wire.kalium.api

import com.wire.kalium.tools.HostProvider
import io.ktor.client.*
import io.ktor.client.call.receive
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*

class KtorHttpClient(
    //private val authApi: AuthApi,
    //private val tokenRepo: TokenRepository
) {
    val ktorHttpClient by lazy {
        HttpClient(OkHttp) {
            install(JsonFeature) {
                serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
                accept(ContentType.Application.Json)
                accept(ContentType.Text.Plain)
            }
            defaultRequest {
                header("Content-Type", "application/json")
                host = HostProvider.host
                url.protocol = URLProtocol.HTTPS
            }
        }
    }
}

class KaliumKtorResult<BodyType : Any>(private val httpResponse: HttpResponse, private val body: BodyType) : KaliumHttpResult<BodyType> {
    override val httpStatusCode: Int
        get() = httpResponse.status.value
    override val headers: Set<Map.Entry<String, List<String>>>
        get() = httpResponse.headers.entries()
    override val resultBody: BodyType
        get() = body
}

suspend inline fun <reified BodyType : Any> wrapKaliumResponse(performRequest: () -> HttpResponse): KaliumHttpResult<BodyType> {
    val result = performRequest()
    return KaliumKtorResult(result, result.receive())
}

fun HttpResponse.isSuccessful(): Boolean = this.status.value in 200..299

