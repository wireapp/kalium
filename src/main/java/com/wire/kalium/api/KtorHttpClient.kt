package com.wire.kalium.api

import com.wire.kalium.tools.HostProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import kotlinx.serialization.json.Json
import okhttp3.logging.HttpLoggingInterceptor

class KtorHttpClient(
        //private val authApi: AuthApi,
        //private val tokenRepo: TokenRepository
) {
    val ktorHttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                val interceptor = HttpLoggingInterceptor()
                interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
                addInterceptor(interceptor)
            }

            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
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

