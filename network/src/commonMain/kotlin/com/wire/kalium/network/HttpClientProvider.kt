package com.wire.kalium.network

import com.wire.kalium.network.tools.HostProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.BearerTokens
import io.ktor.client.features.auth.providers.bearer
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.features.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol

internal fun provideBaseHttpClient(
    kotlinxSerializer: KotlinxSerializer,
    engine: HttpClientEngine,
    isRequestLoggingEnabled: Boolean = false,
    config: HttpClientConfig<*>.() -> Unit = {}
) = HttpClient(engine) {
    defaultRequest {
        header("Content-Type", "application/json")
        host = HostProvider.host
        url.protocol = URLProtocol.HTTPS
    }
    if (isRequestLoggingEnabled) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }
    install(JsonFeature) {
        serializer = kotlinxSerializer
        accept(ContentType.Application.Json)
    }
    config()
}
