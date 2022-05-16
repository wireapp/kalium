package com.wire.kalium.network

import com.wire.kalium.network.serialization.mls
import com.wire.kalium.network.serialization.xprotobuf
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ContentNegotiation
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


/**
 * Provides a [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 * It's Authenticated, and will use the provided [SessionManager] to fill
 * necessary Authentication headers, and refresh tokens as they expire.
 */
internal class AuthenticatedNetworkClient(engine: HttpClientEngine, sessionManager: SessionManager) {
    val httpClient: HttpClient = provideBaseHttpClient(engine, HttpClientOptions.DefaultHost(sessionManager.session().second)) {
        installAuth(sessionManager)
        install(ContentNegotiation) {
            mls()
            xprotobuf()
        }
    }
}

/**
 * Provides a [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 */
internal class UnauthenticatedNetworkClient(engine: HttpClientEngine) {
    // TODO: Make it so it has a base URL
    val httpClient: HttpClient = provideBaseHttpClient(engine, HttpClientOptions.NoDefaultHost)
}

/**
 * Provides a [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 * Unlike others, this one has no strict ties with any API version nor default Base Url
 */
internal class UnboundNetworkClient(engine: HttpClientEngine) {
    val httpClient: HttpClient = provideBaseHttpClient(engine, HttpClientOptions.NoDefaultHost)
}

/**
 * HttpClient with WebSocket (ws or wss) capabilities.
 * It's Authenticated, and will use the provided [SessionManager] to fill
 * necessary Authentication headers, and refresh tokens as they expire.
 */
internal class AuthenticatedWebSocketClient(
    private val engine: HttpClientEngine,
    private val sessionManager: SessionManager
) {

    /**
     * Creates a disposable [HttpClient] for a single use.
     * Once the websocket is disconnected
     * it's okay to use a new HttpClient,
     * as the old one can be dead.
     */
    fun createDisposableHttpClient(): HttpClient =
        provideBaseHttpClient(engine, HttpClientOptions.DefaultHost(sessionManager.session().second)) {
            installAuth(sessionManager)
            install(ContentNegotiation) {
                mls()
                xprotobuf()
            }
            install(WebSockets)
        }
}

internal sealed class HttpClientOptions {
    object NoDefaultHost : HttpClientOptions()
    data class DefaultHost(val serverConfigDTO: ServerConfigDTO) : HttpClientOptions()
}

/**
 * Provides a base [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 *
 * Also enables logs depending on [NetworkLogger] settings.
 *
 * @param engine, the HTTP engine that will perform the requests
 * @param options, some configuration presets
 * @param config, a block that allows further customisation of the [HttpClient]
 */
internal fun provideBaseHttpClient(
    engine: HttpClientEngine,
    options: HttpClientOptions,
    config: HttpClientConfig<*>.() -> Unit = {}
) = HttpClient(engine) {
    defaultRequest {

        // since error response are application/json
        // this header is added by default to all requests
        header(HttpHeaders.ContentType, ContentType.Application.Json)

        when (options) {
            HttpClientOptions.NoDefaultHost -> {/* do nothing */
            }

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

    install(ContentNegotiation) {
        json(KtxSerializer.json)
    }
    expectSuccess = false
    config()
}
