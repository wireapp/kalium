package com.wire.kalium.api

import com.wire.kalium.api.auth.AuthApi
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.auth.*
import io.ktor.client.features.auth.providers.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
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
            }
        }
    }
}
