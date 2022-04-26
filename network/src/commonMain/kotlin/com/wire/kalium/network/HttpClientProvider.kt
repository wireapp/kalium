package com.wire.kalium.network

import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json


sealed class HttpClientOptions {
    object NoDefaultHost : HttpClientOptions()
    data class DefaultHost(val serverConfigDTO: ServerConfigDTO) : HttpClientOptions()
}

internal fun provideBaseHttpClient(
    engine: HttpClientEngine,
    options: HttpClientOptions,
    config: HttpClientConfig<*>.() -> Unit = {}
) = HttpClient(engine) {

    // starting from ktor 2.0.0 expectSuccess is false by default
    expectSuccess = true

    defaultRequest {
        header(HttpHeaders.ContentType, ContentType.Application.Json)

        when (options) {
            HttpClientOptions.NoDefaultHost -> {/* do nothing */ }

            is HttpClientOptions.DefaultHost -> {
                host = options.serverConfigDTO.apiBaseUrl.host
                // the UrlProtocol is intentionally here and not default for both options
                // since any url configuration here will get overwritten by the request configuration
                url.protocol = URLProtocol.HTTPS
            }
        }

    }

    if (NetworkLogger.isRequestLoggingEnabled) {
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }
    // TODO: WebSockets are not supported on iOS. We need to come up with a library-agnostic/platform-specific approach
    install(WebSockets)
    install(ContentNegotiation) {
        json(KtxSerializer.json)
    }
    config()
}
