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

package com.wire.kalium.network.api.v13.authenticated.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.base.authenticated.RemoteBackupApi
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
import com.wire.kalium.network.api.v13.authenticated.AccessTokenApiV13
import com.wire.kalium.network.api.v13.authenticated.AssetApiV13
import com.wire.kalium.network.api.v13.authenticated.CallApiV13
import com.wire.kalium.network.api.v13.authenticated.ClientApiV13
import com.wire.kalium.network.api.v13.authenticated.ConnectionApiV13
import com.wire.kalium.network.api.v13.authenticated.ConversationApiV13
import com.wire.kalium.network.api.v13.authenticated.ConversationHistoryApiV13
import com.wire.kalium.network.api.v13.authenticated.E2EIApiV13
import com.wire.kalium.network.api.v13.authenticated.FeatureConfigApiV13
import com.wire.kalium.network.api.v13.authenticated.KeyPackageApiV13
import com.wire.kalium.network.api.v13.authenticated.LogoutApiV13
import com.wire.kalium.network.api.v13.authenticated.MLSMessageApiV13
import com.wire.kalium.network.api.v13.authenticated.MLSPublicKeyApiV13
import com.wire.kalium.network.api.v13.authenticated.MessageApiV13
import com.wire.kalium.network.api.v13.authenticated.NotificationApiV13
import com.wire.kalium.network.api.v13.authenticated.PreKeyApiV13
import com.wire.kalium.network.api.v13.authenticated.PropertiesApiV13
import com.wire.kalium.network.api.v13.authenticated.RemoteBackupApiV13
import com.wire.kalium.network.api.v13.authenticated.SelfApiV13
import com.wire.kalium.network.api.v13.authenticated.ServerTimeApiV13
import com.wire.kalium.network.api.v13.authenticated.TeamsApiV13
import com.wire.kalium.network.api.v13.authenticated.UpgradePersonalToTeamApiV13
import com.wire.kalium.network.api.v13.authenticated.UserDetailsApiV13
import com.wire.kalium.network.api.v13.authenticated.UserSearchApiV13
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
internal class AuthenticatedNetworkContainerV13 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV13(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV13(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV13(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV13(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV13(
            networkClient,
            EnvelopeProtoMapperImpl()
        )

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV13(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV13(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV13(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV13(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV13(networkClient)

    override val assetApi: AssetApi get() = AssetApiV13(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV13(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV13(networkClient)

    override val selfApi: SelfApi get() = SelfApiV13(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV13(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV13(networkClient)

    override val callApi: CallApi get() = CallApiV13(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV13(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV13(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV13(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV13(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV13(networkClient)

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV13(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV13(networkClient)

    override val cellsHttpClient: HttpClient = networkClient.httpClient

    override val remoteBackupApi: RemoteBackupApi
        get() = RemoteBackupApiV13(networkClient.httpClient, null)
}
