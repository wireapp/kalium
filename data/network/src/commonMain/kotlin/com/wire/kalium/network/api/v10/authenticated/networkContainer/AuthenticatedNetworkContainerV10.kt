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

package com.wire.kalium.network.api.v10.authenticated.networkContainer

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
import com.wire.kalium.network.api.v10.authenticated.AccessTokenApiV10
import com.wire.kalium.network.api.v10.authenticated.AssetApiV10
import com.wire.kalium.network.api.v10.authenticated.CallApiV10
import com.wire.kalium.network.api.v10.authenticated.ClientApiV10
import com.wire.kalium.network.api.v10.authenticated.ConnectionApiV10
import com.wire.kalium.network.api.v10.authenticated.ConversationApiV10
import com.wire.kalium.network.api.v10.authenticated.E2EIApiV10
import com.wire.kalium.network.api.v10.authenticated.FeatureConfigApiV10
import com.wire.kalium.network.api.v10.authenticated.KeyPackageApiV10
import com.wire.kalium.network.api.v10.authenticated.LogoutApiV10
import com.wire.kalium.network.api.v10.authenticated.MLSMessageApiV10
import com.wire.kalium.network.api.v10.authenticated.MLSPublicKeyApiV10
import com.wire.kalium.network.api.v10.authenticated.MessageApiV10
import com.wire.kalium.network.api.v10.authenticated.NotificationApiV10
import com.wire.kalium.network.api.v10.authenticated.PreKeyApiV10
import com.wire.kalium.network.api.v10.authenticated.PropertiesApiV10
import com.wire.kalium.network.api.v10.authenticated.SelfApiV10
import com.wire.kalium.network.api.v10.authenticated.ServerTimeApiV10
import com.wire.kalium.network.api.v10.authenticated.ConversationHistoryApiV10
import com.wire.kalium.network.api.v10.authenticated.TeamsApiV10
import com.wire.kalium.network.api.v10.authenticated.UpgradePersonalToTeamApiV10
import com.wire.kalium.network.api.v9.authenticated.sync.SyncApiV9
import com.wire.kalium.network.api.v10.authenticated.UserDetailsApiV10
import com.wire.kalium.network.api.v10.authenticated.UserSearchApiV10
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
internal class AuthenticatedNetworkContainerV10 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV10(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV10(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV10(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV10(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV10(
            networkClient,
            EnvelopeProtoMapperImpl()
        )

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV10(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV10(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV10(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV10(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV10(networkClient)

    override val assetApi: AssetApi get() = AssetApiV10(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV10(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV10(networkClient)

    override val selfApi: SelfApi get() = SelfApiV10(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV10(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV10(networkClient)

    override val callApi: CallApi get() = CallApiV10(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV10(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV10(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV10(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV10(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV10()

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV10(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV10(networkClient)

    override val syncApi: SyncApi get() = SyncApiV9(networkClient, syncApiBaseUrl)

    override val cellsHttpClient: HttpClient = networkClient.httpClient
}
