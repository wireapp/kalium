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

package com.wire.kalium.network.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
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
import com.wire.kalium.network.api.v2.authenticated.networkContainer.AuthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v4.authenticated.networkContainer.AuthenticatedNetworkContainerV4
import com.wire.kalium.network.api.v5.authenticated.networkContainer.AuthenticatedNetworkContainerV5
import com.wire.kalium.network.api.v6.authenticated.networkContainer.AuthenticatedNetworkContainerV6
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams

@Suppress("MagicNumber")
interface AuthenticatedNetworkContainer {

    /**
     * Clear any cached token on the http clients. This will trigger a reloading
     * of the access token from the session manager on the next request.
     */
    suspend fun clearCachedToken()

    val accessTokenApi: AccessTokenApi

    val logoutApi: LogoutApi

    val clientApi: ClientApi

    val messageApi: MessageApi

    val mlsMessageApi: MLSMessageApi

    val conversationApi: ConversationApi

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

    val connectionApi: ConnectionApi

    val featureConfigApi: FeatureConfigApi

    val mlsPublicKeyApi: MLSPublicKeyApi

    val propertiesApi: PropertiesApi

    companion object {

        @Suppress("LongParameterList", "LongMethod")
        fun create(
            networkStateObserver: NetworkStateObserver,
            sessionManager: SessionManager,
            selfUserId: UserId,
            userAgent: String,
            certificatePinning: CertificatePinning,
            mockEngine: HttpClientEngine?,
            kaliumLogger: KaliumLogger,
        ): AuthenticatedNetworkContainer {

            KaliumUserAgentProvider.setUserAgent(userAgent)

            return when (val version = sessionManager.serverConfig().metaData.commonApiVersion.version) {
                0 -> AuthenticatedNetworkContainerV0(
                    networkStateObserver,
                    sessionManager,
                    certificatePinning,
                    mockEngine,
                    kaliumLogger,
                )

                1 -> AuthenticatedNetworkContainerV0(
                    networkStateObserver,
                    sessionManager,
                    certificatePinning,
                    mockEngine,
                    kaliumLogger,
                )

                2 -> AuthenticatedNetworkContainerV2(
                    networkStateObserver,
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    kaliumLogger,
                )

                // this is intentional since we should drop support for api v3
                // and we default back to v2
                3 -> AuthenticatedNetworkContainerV2(
                    networkStateObserver,
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    kaliumLogger,
                )

                4 -> AuthenticatedNetworkContainerV4(
                    networkStateObserver,
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    kaliumLogger,
                )

                5 -> AuthenticatedNetworkContainerV5(
                    networkStateObserver,
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    kaliumLogger,
                )

                6 -> AuthenticatedNetworkContainerV6(
                    networkStateObserver,
                    sessionManager,
                    selfUserId,
                    certificatePinning,
                    mockEngine,
                    kaliumLogger,
                )

                else -> error("Unsupported version: $version")
            }
        }
    }
}

internal interface AuthenticatedHttpClientProvider {
    val backendConfig: ServerConfigDTO.Links
    val networkClient: AuthenticatedNetworkClient
    val websocketClient: AuthenticatedWebSocketClient
    val networkClientWithoutCompression: AuthenticatedNetworkClient
    suspend fun clearCachedToken()
}

internal class AuthenticatedHttpClientProviderImpl(
    private val sessionManager: SessionManager,
    private val networkStateObserver: NetworkStateObserver,
    private val accessTokenApi: (httpClient: HttpClient) -> AccessTokenApi,
    private val engine: HttpClientEngine,
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
            oldAccessToken = oldTokens!!.accessToken,
            oldRefreshToken = oldTokens!!.refreshToken
        )
        BearerTokens(
            accessToken = newSession.accessToken,
            refreshToken = newSession.refreshToken
        )
    }

    private val bearerAuthProvider: BearerAuthProvider = BearerAuthProvider(refreshToken, loadToken, { true }, null)

    override val backendConfig = sessionManager.serverConfig().links

    override val networkClient by lazy {
        AuthenticatedNetworkClient(
            networkStateObserver,
            engine,
            sessionManager.serverConfig(),
            bearerAuthProvider,
            kaliumLogger
        )
    }
    override val websocketClient by lazy {
        AuthenticatedWebSocketClient(
            networkStateObserver,
            engine,
            bearerAuthProvider,
            sessionManager.serverConfig(),
            kaliumLogger
        )
    }
    override val networkClientWithoutCompression by lazy {
        AuthenticatedNetworkClient(
            networkStateObserver,
            engine,
            sessionManager.serverConfig(),
            bearerAuthProvider,
            kaliumLogger,
            installCompression = false
        )
    }
}
