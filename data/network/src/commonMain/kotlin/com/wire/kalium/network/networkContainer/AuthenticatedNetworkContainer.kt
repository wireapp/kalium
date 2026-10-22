/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.network.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.base.authenticated.SFTApi
import com.wire.kalium.network.api.base.authenticated.ServerTimeApi
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.UpgradePersonalToTeamApi
import com.wire.kalium.network.api.base.authenticated.WildCardApi
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.history.ConversationHistoryApi
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.api.v0.authenticated.networkContainer.AuthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v10.authenticated.networkContainer.AuthenticatedNetworkContainerV10
import com.wire.kalium.network.api.v11.authenticated.networkContainer.AuthenticatedNetworkContainerV11
import com.wire.kalium.network.api.v12.authenticated.networkContainer.AuthenticatedNetworkContainerV12
import com.wire.kalium.network.api.v13.authenticated.networkContainer.AuthenticatedNetworkContainerV13
import com.wire.kalium.network.api.v14.authenticated.networkContainer.AuthenticatedNetworkContainerV14
import com.wire.kalium.network.api.v15.authenticated.networkContainer.AuthenticatedNetworkContainerV15
import com.wire.kalium.network.api.v16.authenticated.networkContainer.AuthenticatedNetworkContainerV16
import com.wire.kalium.network.api.v17.authenticated.networkContainer.AuthenticatedNetworkContainerV17
import com.wire.kalium.network.api.v18.authenticated.networkContainer.AuthenticatedNetworkContainerV18
import com.wire.kalium.network.api.v19.authenticated.networkContainer.AuthenticatedNetworkContainerV19
import com.wire.kalium.network.api.v2.authenticated.networkContainer.AuthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v4.authenticated.networkContainer.AuthenticatedNetworkContainerV4
import com.wire.kalium.network.api.v5.authenticated.networkContainer.AuthenticatedNetworkContainerV5
import com.wire.kalium.network.api.v6.authenticated.networkContainer.AuthenticatedNetworkContainerV6
import com.wire.kalium.network.api.v7.authenticated.networkContainer.AuthenticatedNetworkContainerV7
import com.wire.kalium.network.api.v8.authenticated.networkContainer.AuthenticatedNetworkContainerV8
import com.wire.kalium.network.api.v9.authenticated.networkContainer.AuthenticatedNetworkContainerV9
import com.wire.kalium.network.api.vcommon.SFTApiImpl
import com.wire.kalium.network.HttpTrafficObserver
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Suppress("MagicNumber")
interface AuthenticatedNetworkContainer {

    /**
     * Clear any cached token on the http clients. This will trigger a reloading
     * of the access token from the session manager on the next request.
     */
    suspend fun clearCachedToken()

    /** Cancel and join all caller requests/collectors before closing. Injected mock engines remain caller-owned. */
    suspend fun close()

    /**
     * Close open WebSockets and wait until their clients finish; HTTP requests and later WebSockets still work.
     * The engine may finish closing the native socket afterwards. Stop collectors first, or they may reconnect.
     */
    suspend fun closeWebSockets()

    val accessTokenApi: AccessTokenApi

    val logoutApi: LogoutApi

    val clientApi: ClientApi

    val messageApi: MessageApi

    val mlsMessageApi: MLSMessageApi

    val conversationApi: ConversationApi

    val conversationHistoryApi: ConversationHistoryApi

    val keyPackageApi: KeyPackageApi

    val e2eiApi: E2EIApi

    val preKeyApi: PreKeyApi

    val assetApi: AssetApi

    val notificationApi: NotificationApi

    val teamsApi: TeamsApi

    val selfApi: SelfApi

    val userDetailsApi: UserDetailsApi

    val userSearchApi: UserSearchApi

    val callApi: CallApi

    val sftApi: SFTApi

    val connectionApi: ConnectionApi

    val featureConfigApi: FeatureConfigApi

    val mlsPublicKeyApi: MLSPublicKeyApi

    val propertiesApi: PropertiesApi

    val wildCardApi: WildCardApi

    val upgradePersonalToTeamApi: UpgradePersonalToTeamApi

    val serverTimeApi: ServerTimeApi

    val meetingApi: MeetingApi

    val cellsHttpClient: HttpClient

    companion object {

        @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
        fun create(
            sessionManager: SessionManager,
            selfUserId: UserId,
            userAgent: String,
            certificatePinning: CertificatePinning,
            mockEngine: HttpClientEngine?,
            mockWebSocketSession: WebSocketSession?,
            kaliumLogger: KaliumLogger,
            httpTrafficObserver: HttpTrafficObserver? = null,
        ): AuthenticatedNetworkContainer {

            KaliumUserAgentProvider.setUserAgent(userAgent)

            // Precomputed once here (rather than left to each version's own default) so that
            // [httpTrafficObserver], when provided, is installed regardless of API version -
            // without needing to touch every per-version container class below.
            val engine = mockEngine ?: defaultHttpEngine(
                serverConfigDTOApiProxy = sessionManager.serverConfig().links.apiProxy,
                proxyCredentials = sessionManager.proxyCredentials(),
                certificatePinning = certificatePinning,
                httpTrafficObserver = httpTrafficObserver,
            )

            return when (val version = sessionManager.serverConfig().metaData.commonApiVersion.version) {
                0 -> AuthenticatedNetworkContainerV0(
                    sessionManager,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                1 -> AuthenticatedNetworkContainerV0(
                    sessionManager,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                2 -> AuthenticatedNetworkContainerV2(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                // this is intentional since we should drop support for api v3
                // and we default back to v2
                3 -> AuthenticatedNetworkContainerV2(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                4 -> AuthenticatedNetworkContainerV4(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                5 -> AuthenticatedNetworkContainerV5(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                6 -> AuthenticatedNetworkContainerV6(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                7 -> AuthenticatedNetworkContainerV7(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                8 -> AuthenticatedNetworkContainerV8(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                9 -> AuthenticatedNetworkContainerV9(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                10 -> AuthenticatedNetworkContainerV10(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                11 -> AuthenticatedNetworkContainerV11(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                12 -> AuthenticatedNetworkContainerV12(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                13 -> AuthenticatedNetworkContainerV13(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                14 -> AuthenticatedNetworkContainerV14(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                15 -> AuthenticatedNetworkContainerV15(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                16 -> AuthenticatedNetworkContainerV16(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                17 -> AuthenticatedNetworkContainerV17(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                18 -> AuthenticatedNetworkContainerV18(
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                19 -> AuthenticatedNetworkContainerV19(
                    sessionManager,
                    nomadServiceUrl,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    mockWebSocketSession,
                    kaliumLogger,
                    engine = engine,
                )

                // You can use scripts/generate_new_api_version.sh or gradle task network:generateNewApiVersion to
                // bump API version and generate all needed classes
                else -> error("Unsupported version: $version")
            }
        }
    }
}

internal interface AuthenticatedHttpClientProvider {
    suspend fun close()
    suspend fun closeWebSockets()
    val cellsHttpClient: HttpClient
        get() = networkClient.httpClient
    val backendConfig: ServerConfigDTO.Links
    val networkClient: AuthenticatedNetworkClient
    val websocketClient: AuthenticatedWebSocketClient
    val networkClientWithoutCompression: AuthenticatedNetworkClient

    /** For SFT servers, which are not Wire backends and must never receive the Wire access token. */
    val sftNetworkClient: UnboundNetworkClient
    val sftApi: SFTApi
    suspend fun clearCachedToken()
}

@Suppress("LongParameterList")
internal class AuthenticatedHttpClientProviderImpl(
    private val sessionManager: SessionManager,
    private val accessTokenApi: (httpClient: HttpClient) -> AccessTokenApi,
    private val engine: HttpClientEngine,
    private val ownsEngine: Boolean,
    private val webSocketSessionProvider: ((HttpClient, String) -> WebSocketSession)?,
    private val kaliumLogger: KaliumLogger,
) : AuthenticatedHttpClientProvider {

    override suspend fun clearCachedToken() {
        bearerAuthProvider.clearToken()
    }

    private val loadToken: suspend () -> BearerTokens? = {
        sessionManager.session()?.let { session ->
            BearerTokens(accessToken = session.accessToken, refreshToken = session.refreshToken)
        }
    }

    private val refreshToken: suspend RefreshTokensParams.() -> BearerTokens = {
        val areOldTokensNull = oldTokens == null
        kaliumLogger.i("Auth tokens are being refreshed")
        if (areOldTokensNull) {
            kaliumLogger.e("Old Auth tokens are null! Someone call the doctor! This should never happen")
        }
        val newSession = sessionManager.updateToken(
            accessTokenApi = accessTokenApi(client),
            oldRefreshToken = oldTokens?.refreshToken
        )
        BearerTokens(
            accessToken = newSession.accessToken,
            refreshToken = newSession.refreshToken
        )
    }

    private val bearerAuthProvider: BearerAuthProvider = BearerAuthProvider(refreshToken, loadToken, { true }, null)

    override val backendConfig = sessionManager.serverConfig().links

    private val networkClientDelegate = lazy {
        AuthenticatedNetworkClient(
            engine,
            sessionManager.serverConfig(),
            bearerAuthProvider,
            kaliumLogger
        )
    }
    private val websocketClientDelegate = lazy {
        AuthenticatedWebSocketClient(
            engine,
            bearerAuthProvider,
            sessionManager.serverConfig(),
            kaliumLogger,
            webSocketSessionProvider
        )
    }
    private val networkClientWithoutCompressionDelegate = lazy {
        AuthenticatedNetworkClient(
            engine,
            sessionManager.serverConfig(),
            bearerAuthProvider,
            kaliumLogger,
            installCompression = false
        )
    }
    private val sftNetworkClientDelegate = lazy { UnboundNetworkClient(engine) }

    override val networkClient by networkClientDelegate
    override val websocketClient by websocketClientDelegate
    override val networkClientWithoutCompression by networkClientWithoutCompressionDelegate
    override val sftNetworkClient by sftNetworkClientDelegate
    override val sftApi: SFTApi get() = SFTApiImpl(sftNetworkClient)
    private val closeMutex = Mutex()
    private var closeFailure: Throwable? = null
    private var closed = false

    @Suppress("TooGenericExceptionCaught")
    override suspend fun close() = withContext(NonCancellable) {
        closeMutex.withLock {
            closeFailure?.let { throw it }
            if (closed) return@withLock
            try {
                if (websocketClientDelegate.isInitialized()) websocketClientDelegate.value.close()
                val clients = listOfNotNull(
                    networkClientDelegate.takeIf { it.isInitialized() }?.value?.httpClient,
                    networkClientWithoutCompressionDelegate.takeIf { it.isInitialized() }?.value?.httpClient,
                    sftNetworkClientDelegate.takeIf { it.isInitialized() }?.value?.httpClient,
                )
                clients.forEach { it.close() }
                clients.forEach { it.coroutineContext.job.join() }
                if (ownsEngine) {
                    engine.close()
                    engine.coroutineContext.job.join()
                }
                closed = true
            } catch (failure: Throwable) {
                closeFailure = failure
                throw failure
            }
        }
    }

    override suspend fun closeWebSockets() {
        if (websocketClientDelegate.isInitialized()) websocketClientDelegate.value.closeSessions()
    }
}
