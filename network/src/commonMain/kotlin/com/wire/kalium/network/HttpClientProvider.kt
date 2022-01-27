package com.wire.kalium.network

import com.wire.kalium.network.tools.HostProvider
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun provideBaseHttpClient(
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
    install(ContentNegotiation) {
        json(KtxSerializer.json)
    }
    config()
}
