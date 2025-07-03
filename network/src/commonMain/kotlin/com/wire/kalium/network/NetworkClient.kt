/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import com.wire.kalium.network.serialization.mls
import com.wire.kalium.network.serialization.xprotobuf
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.installWireDefaultRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.websocket.WebSocketSession

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
    kaliumLogger: KaliumLogger,
    installCompression: Boolean = true
) {
    val httpClient: HttpClient = provideBaseHttpClient(
        engine,
        kaliumLogger,
        installCompression
    ) {
        installWireDefaultRequest(serverConfigDTO)
        installAuth(bearerAuthProvider)
        install(ContentNegotiation) {
            json(KtxSerializer.json)
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
    val httpClient: HttpClient = provideBaseHttpClient(engine, kaliumLogger) {
        installWireDefaultRequest(backendLinks)
        install(ContentNegotiation) {
            json(KtxSerializer.json)
        }
    }
}

/**
 * Provides a [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 * Unlike others, this one has no strict ties with any API version nor default Base Url
 */
internal class UnboundNetworkClient(
    engine: HttpClientEngine
) {
    val httpClient: HttpClient = provideBaseHttpClient(engine, kaliumLogger) {
        install(ContentNegotiation) {
            json(KtxSerializer.json)
        }
    }
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
    private val kaliumLogger: KaliumLogger,
    private val webSocketSessionProvider: ((HttpClient, String) -> WebSocketSession)? = null
) {

    /**
     * Creates a [Url] for the WebSocket connection with the ability to be versioned.
     */
    fun createWSSUrl(shouldAddApiVersion: Boolean = false, vararg path: String): Url {
        val baseUrl = Url(serverConfigDTO.links.webSocket)
        return URLBuilder(
            protocol = URLProtocol.WSS,
            host = baseUrl.host,
            port = URLProtocol.WSS.defaultPort,
        )
            .appendPathSegments(baseUrl.rawSegments)
            .appendPathSegments(
                if (shouldAddApiVersion) "v${serverConfigDTO.metaData.commonApiVersion.version}" else ""
            ).appendPathSegments(path.toList())
            .build()
    }

    /**
     * Creates a disposable [HttpClient] for a single use.
     * Once the websocket is disconnected
     * it's okay to use a new HttpClient,
     * as the old one can be dead.
     */
    private fun createDisposableHttpClient(): HttpClient =
        provideBaseHttpClient(engine, kaliumLogger) {
            installWireDefaultRequest(serverConfigDTO)
            installAuth(bearerAuthProvider)
            install(ContentNegotiation) {
                json(KtxSerializer.json)
                mls()
                xprotobuf()
            }
            install(WebSockets) {
                // Depending on the Engine (OkHttp for example), we might
                // need to set this value there too, as this here won't work
                pingIntervalMillis = WEBSOCKET_PING_INTERVAL_MILLIS
            }
        }

    suspend fun createWebSocketSession(clientId: String, block: HttpRequestBuilder.() -> Unit): WebSocketSession {
        val client = createDisposableHttpClient()
        return webSocketSessionProvider?.let {
            return it(client, clientId)
        } ?: client.webSocketSession(block)
    }
}

internal fun provideBaseHttpClient(
    engine: HttpClientEngine,
    kaliumLogger: KaliumLogger,
    installCompression: Boolean = true,
    config: HttpClientConfig<*>.() -> Unit = {}
) = HttpClient(engine) {
    install(UserAgent) {
        agent = KaliumUserAgentProvider.userAgent
    }

    if (NetworkUtilLogger.isRequestLoggingEnabled) {
        install(KaliumKtorCustomLogging) {
            this.level = LogLevel.ALL
            this.kaliumLogger = kaliumLogger
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
internal const val WEBSOCKET_TIMEOUT = 30_000L
