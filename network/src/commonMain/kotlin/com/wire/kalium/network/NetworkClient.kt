package com.wire.kalium.network

import com.wire.kalium.network.api.versioning.VersionApiImpl
import com.wire.kalium.network.serialization.mls
import com.wire.kalium.network.serialization.xprotobuf
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.session.installAuth
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.WireDefaultRequest
import com.wire.kalium.network.utils.WireServerMetaDataConfig
import com.wire.kalium.network.utils.config
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ContentNegotiation
import io.ktor.client.plugins.UserAgent
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
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json


/**
 * Provides a [HttpClient] that has all the
 * needed configurations to talk with a Wire backend, like
 * Serialization, and Content Negotiation.
 * It's Authenticated, and will use the provided [SessionManager] to fill
 * necessary Authentication headers, and refresh tokens as they expire.
 */
internal class AuthenticatedNetworkClient(engine: HttpClientEngine, sessionManager: SessionManager) {
    val httpClient: HttpClient = provideBaseHttpClient(engine) {
        installWireBaseUrl(sessionManager.session().second)
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
internal class UnauthenticatedNetworkClient(
    engine: HttpClientEngine,
    backendLinks: ServerConfigDTO.Links,
    serverMetaDataManager: ServerMetaDataManager
) {
    val httpClient: HttpClient = provideBaseHttpClient(engine) {
        install(WireDefaultRequest) {
            config {
                WireServerMetaDataConfig().apply {
                    loadServerData = {
                        serverMetaDataManager.getLocalMetaData(backendLinks)
                    }

                    fetchAndStoreMetadata = { httpClient ->
                        val versionApi = VersionApiImpl(httpClient)
                        when (val result = versionApi.fetchApiVersion(backendLinks.api)) {
                            is NetworkResponse.Success ->
                                serverMetaDataManager.storeBackend(
                                    backendLinks, result.value
                                )
                            is NetworkResponse.Error -> null
                        }
                    }

                    buildDefaultRequest = {
                        header(HttpHeaders.ContentType, ContentType.Application.Json)
                        with(it) {
                            val apiBaseUrl = links.api
                            // enforce https as url protocol
                            url.protocol = URLProtocol.HTTPS
                            // add the default host
                            url.host = apiBaseUrl.host
                            // for api version 0 no api version should be added to the request
                            url.encodedPath =
                                if (shouldAddApiVersion(metaData.commonApiVersion.version))
                                    apiBaseUrl.encodedPath + "v${metaData.commonApiVersion.version}/"
                                else apiBaseUrl.encodedPath
                        }
                    }
                }
            }
        }
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
    private val engine: HttpClientEngine, private val sessionManager: SessionManager
) {
    /**
     * Creates a disposable [HttpClient] for a single use.
     * Once the websocket is disconnected
     * it's okay to use a new HttpClient,
     * as the old one can be dead.
     */
    fun createDisposableHttpClient(): HttpClient =
        provideBaseHttpClient(engine) {
            installWireBaseUrl(sessionManager.session().second)
            installAuth(sessionManager)
            install(ContentNegotiation) {
                mls()
                xprotobuf()
            }
            install(WebSockets)
        }
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
private fun HttpClientConfig<*>.installWireBaseUrl(backend: ServerConfigDTO) {
    defaultRequest {
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        with(backend) {
            val apiBaseUrl = backend.links.api
            // enforce https as url protocol
            url.protocol = URLProtocol.HTTPS
            // add the default host
            url.host = apiBaseUrl.host
            // for api version 0 no api version should be added to the request
            url.encodedPath =
                if (shouldAddApiVersion(metaData.commonApiVersion.version))
                    apiBaseUrl.encodedPath + "v${metaData.commonApiVersion.version}/"
                else apiBaseUrl.encodedPath
        }
    }
}

internal fun provideBaseHttpClient(
    engine: HttpClientEngine, config: HttpClientConfig<*>.() -> Unit = {}
) = HttpClient(engine) {
    install(UserAgent) {
        agent = "007"
    }

    if (true) {
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

internal fun shouldAddApiVersion(apiVersion: Int): Boolean = apiVersion >= MINIMUM_API_VERSION_TO_ADD
private const val MINIMUM_API_VERSION_TO_ADD = 1
