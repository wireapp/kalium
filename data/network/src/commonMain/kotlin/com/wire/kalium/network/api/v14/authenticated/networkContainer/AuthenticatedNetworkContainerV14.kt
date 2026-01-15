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

package com.wire.kalium.network.api.v14.authenticated.networkContainer

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
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v14.authenticated.AccessTokenApiV14
import com.wire.kalium.network.api.v14.authenticated.AssetApiV14
import com.wire.kalium.network.api.v14.authenticated.CallApiV14
import com.wire.kalium.network.api.v14.authenticated.ClientApiV14
import com.wire.kalium.network.api.v14.authenticated.ConnectionApiV14
import com.wire.kalium.network.api.v14.authenticated.ConversationApiV14
import com.wire.kalium.network.api.v14.authenticated.ConversationHistoryApiV14
import com.wire.kalium.network.api.v14.authenticated.E2EIApiV14
import com.wire.kalium.network.api.v14.authenticated.FeatureConfigApiV14
import com.wire.kalium.network.api.v14.authenticated.KeyPackageApiV14
import com.wire.kalium.network.api.v14.authenticated.LogoutApiV14
import com.wire.kalium.network.api.v14.authenticated.MLSMessageApiV14
import com.wire.kalium.network.api.v14.authenticated.MLSPublicKeyApiV14
import com.wire.kalium.network.api.v14.authenticated.MessageApiV14
import com.wire.kalium.network.api.v14.authenticated.NotificationApiV14
import com.wire.kalium.network.api.v14.authenticated.PreKeyApiV14
import com.wire.kalium.network.api.v14.authenticated.PropertiesApiV14
import com.wire.kalium.network.api.v14.authenticated.SelfApiV14
import com.wire.kalium.network.api.v14.authenticated.ServerTimeApiV14
import com.wire.kalium.network.api.v14.authenticated.TeamsApiV14
import com.wire.kalium.network.api.v14.authenticated.UpgradePersonalToTeamApiV14
import com.wire.kalium.network.api.v14.authenticated.UserDetailsApiV14
import com.wire.kalium.network.api.v14.authenticated.UserSearchApiV14
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
internal class AuthenticatedNetworkContainerV14 internal constructor(
    private val sessionManager: SessionManager,
    private val selfUserId: UserId,
    certificatePinning: CertificatePinning,
    mockEngine: HttpClientEngine?,
    mockWebSocketSession: WebSocketSession?,
    kaliumLogger: KaliumLogger,
    engine: HttpClientEngine = mockEngine ?: defaultHttpEngine(
        serverConfigDTOApiProxy = sessionManager.serverConfig().links.apiProxy,
        proxyCredentials = sessionManager.proxyCredentials(),
        certificatePinning = certificatePinning
    )
) : AuthenticatedNetworkContainer,
    AuthenticatedHttpClientProvider by AuthenticatedHttpClientProviderImpl(
        sessionManager = sessionManager,
        accessTokenApi = { httpClient -> AccessTokenApiV14(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV14(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV14(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV14(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV14(
            networkClient,
            EnvelopeProtoMapperImpl()
        )

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV14(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV14(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV14(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV14(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV14(networkClient)

    override val assetApi: AssetApi get() = AssetApiV14(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV14(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV14(networkClient)

    override val selfApi: SelfApi get() = SelfApiV14(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV14(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV14(networkClient)

    override val callApi: CallApi get() = CallApiV14(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV14(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV14(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV14(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV14(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV14(networkClient)

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV14(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV14(networkClient)

    override val cellsHttpClient: HttpClient = networkClient.httpClient
}
