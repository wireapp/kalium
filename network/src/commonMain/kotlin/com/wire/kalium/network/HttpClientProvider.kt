package com.wire.kalium.network

import com.wire.kalium.network.tools.HostProvider
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.json.JsonPlugin
import io.ktor.client.plugins.json.serializer.KotlinxSerializer
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
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
    install(JsonPlugin) {
        serializer = kotlinxSerializer
        accept(ContentType.Application.Json)
    }
    config()
}
