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

package com.wire.kalium.network.api.v11.authenticated.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.CallApi
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
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapperImpl
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.base.authenticated.sync.SyncApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v11.authenticated.AccessTokenApiV11
import com.wire.kalium.network.api.v11.authenticated.AssetApiV11
import com.wire.kalium.network.api.v11.authenticated.CallApiV11
import com.wire.kalium.network.api.v11.authenticated.ClientApiV11
import com.wire.kalium.network.api.v11.authenticated.ConnectionApiV11
import com.wire.kalium.network.api.v11.authenticated.ConversationApiV11
import com.wire.kalium.network.api.v11.authenticated.E2EIApiV11
import com.wire.kalium.network.api.v11.authenticated.FeatureConfigApiV11
import com.wire.kalium.network.api.v11.authenticated.KeyPackageApiV11
import com.wire.kalium.network.api.v11.authenticated.LogoutApiV11
import com.wire.kalium.network.api.v11.authenticated.MLSMessageApiV11
import com.wire.kalium.network.api.v11.authenticated.MLSPublicKeyApiV11
import com.wire.kalium.network.api.v11.authenticated.MessageApiV11
import com.wire.kalium.network.api.v11.authenticated.NotificationApiV11
import com.wire.kalium.network.api.v11.authenticated.PreKeyApiV11
import com.wire.kalium.network.api.v11.authenticated.PropertiesApiV11
import com.wire.kalium.network.api.v11.authenticated.SelfApiV11
import com.wire.kalium.network.api.v11.authenticated.ServerTimeApiV11
import com.wire.kalium.network.api.v11.authenticated.ConversationHistoryApiV11
import com.wire.kalium.network.api.v11.authenticated.TeamsApiV11
import com.wire.kalium.network.api.v11.authenticated.sync.SyncApiV11
import com.wire.kalium.network.api.v11.authenticated.UpgradePersonalToTeamApiV11
import com.wire.kalium.network.api.v11.authenticated.UserDetailsApiV11
import com.wire.kalium.network.api.v11.authenticated.UserSearchApiV11
import com.wire.kalium.network.api.vcommon.WildCardApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.websocket.WebSocketSession

@Suppress("LongParameterList")
internal class AuthenticatedNetworkContainerV11 internal constructor(
    private val sessionManager: SessionManager,
    private val selfUserId: UserId,
    certificatePinning: CertificatePinning,
    mockEngine: HttpClientEngine?,
    mockWebSocketSession: WebSocketSession?,
    kaliumLogger: KaliumLogger,
    private val syncApiBaseUrl: String? = null,
    engine: HttpClientEngine = mockEngine ?: defaultHttpEngine(
        serverConfigDTOApiProxy = sessionManager.serverConfig().links.apiProxy,
        proxyCredentials = sessionManager.proxyCredentials(),
        certificatePinning = certificatePinning
    )
) : AuthenticatedNetworkContainer,
    AuthenticatedHttpClientProvider by AuthenticatedHttpClientProviderImpl(
        sessionManager = sessionManager,
        accessTokenApi = { httpClient -> AccessTokenApiV11(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV11(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV11(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV11(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV11(
            networkClient,
            EnvelopeProtoMapperImpl()
        )

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV11(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV11(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV11(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV11(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV11(networkClient)

    override val assetApi: AssetApi get() = AssetApiV11(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV11(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV11(networkClient)

    override val selfApi: SelfApi get() = SelfApiV11(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV11(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV11(networkClient)

    override val callApi: CallApi get() = CallApiV11(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV11(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV11(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV11(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV11(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV11(networkClient)

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV11(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV11(networkClient)

    override val syncApi: SyncApi get() = SyncApiV11(networkClient, syncApiBaseUrl)

    override val cellsHttpClient: HttpClient = networkClient.httpClient
}
