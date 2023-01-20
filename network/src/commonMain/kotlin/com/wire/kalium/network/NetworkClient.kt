package com.wire.kalium.network

import com.wire.kalium.network.serialization.mls
import com.wire.kalium.network.serialization.xprotobuf
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.installWireDefaultRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json

/**
 * Provides a [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 * It's Authenticated, and will use the provided [SessionManager] to fill
 * necessary Authentication headers, and refresh tokens as they expire.
 */
internal class AuthenticatedNetworkClient(
    engine: HttpClientEngine,
    serverConfigDTO: ServerConfigDTO,
    bearerAuthProvider: BearerAuthProvider,
    installCompression: Boolean = true
) {
    val httpClient: HttpClient = provideBaseHttpClient(engine, installCompression) {
        installWireDefaultRequest(serverConfigDTO)
        installAuth(bearerAuthProvider)
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
internal class UnauthenticatedNetworkClient(
    engine: HttpClientEngine,
    backendLinks: ServerConfigDTO
) {
    val httpClient: HttpClient = provideBaseHttpClient(engine) {
        installWireDefaultRequest(backendLinks)
    }
}

/**
 * Provides a [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 * Unlike others, this one has no strict ties with any API version nor default Base Url
 */
internal class UnboundNetworkClient(engine: HttpClientEngine) {
    val httpClient: HttpClient = provideBaseHttpClient(engine)
}

/**
 * HttpClient with WebSocket (ws or wss) capabilities.
 * It's Authenticated, and will use the provided [SessionManager] to fill
 * necessary Authentication headers, and refresh tokens as they expire.
 */
internal class AuthenticatedWebSocketClient(
    private val engine: HttpClientEngine,
    private val bearerAuthProvider: BearerAuthProvider,
    private val serverConfigDTO: ServerConfigDTO,
) {
    /**
     * Creates a disposable [HttpClient] for a single use.
     * Once the websocket is disconnected
     * it's okay to use a new HttpClient,
     * as the old one can be dead.
     */
    fun createDisposableHttpClient(): HttpClient =
        provideBaseHttpClient(engine) {
            installWireDefaultRequest(serverConfigDTO)
            installAuth(bearerAuthProvider)
            install(ContentNegotiation) {
                mls()
                xprotobuf()
            }
            install(WebSockets) {
                // Depending on the Engine (OkHttp for example), we might
                // need to set this value there too, as this here won't work
                pingInterval = WEBSOCKET_PING_INTERVAL_MILLIS
            }
        }
}

internal fun provideBaseHttpClient(
    engine: HttpClientEngine,
    installCompression: Boolean = true,
    config: HttpClientConfig<*>.() -> Unit = {}
) = HttpClient(engine) {

    if (NetworkLogger.isRequestLoggingEnabled) {
        install(KaliumKtorCustomLogging) {
            level = LogLevel.ALL
        }
    }

    if (installCompression) {
        install(ContentEncoding) {
            gzip()
            identity()
        }
    }

    install(ContentNegotiation) {
        json(KtxSerializer.json)
    }
    expectSuccess = false
    config()
}

internal fun shouldAddApiVersion(apiVersion: Int): Boolean = apiVersion >= MINIMUM_API_VERSION_TO_ADD
private const val MINIMUM_API_VERSION_TO_ADD = 1
internal const val WEBSOCKET_PING_INTERVAL_MILLIS = 20_000L
