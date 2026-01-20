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

package com.wire.kalium.network.api.v12.authenticated.networkContainer

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
import com.wire.kalium.network.api.v12.authenticated.AccessTokenApiV12
import com.wire.kalium.network.api.v12.authenticated.AssetApiV12
import com.wire.kalium.network.api.v12.authenticated.CallApiV12
import com.wire.kalium.network.api.v12.authenticated.ClientApiV12
import com.wire.kalium.network.api.v12.authenticated.ConnectionApiV12
import com.wire.kalium.network.api.v12.authenticated.ConversationApiV12
import com.wire.kalium.network.api.v12.authenticated.ConversationHistoryApiV12
import com.wire.kalium.network.api.v12.authenticated.E2EIApiV12
import com.wire.kalium.network.api.v12.authenticated.FeatureConfigApiV12
import com.wire.kalium.network.api.v12.authenticated.KeyPackageApiV12
import com.wire.kalium.network.api.v12.authenticated.LogoutApiV12
import com.wire.kalium.network.api.v12.authenticated.MLSMessageApiV12
import com.wire.kalium.network.api.v12.authenticated.MLSPublicKeyApiV12
import com.wire.kalium.network.api.v12.authenticated.MessageApiV12
import com.wire.kalium.network.api.v12.authenticated.NotificationApiV12
import com.wire.kalium.network.api.v12.authenticated.PreKeyApiV12
import com.wire.kalium.network.api.v12.authenticated.PropertiesApiV12
import com.wire.kalium.network.api.v12.authenticated.RemoteBackupApiV12
import com.wire.kalium.network.api.v12.authenticated.SelfApiV12
import com.wire.kalium.network.api.v12.authenticated.ServerTimeApiV12
import com.wire.kalium.network.api.v12.authenticated.TeamsApiV12
import com.wire.kalium.network.api.v12.authenticated.UpgradePersonalToTeamApiV12
import com.wire.kalium.network.api.v12.authenticated.UserDetailsApiV12
import com.wire.kalium.network.api.v12.authenticated.UserSearchApiV12
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
internal class AuthenticatedNetworkContainerV12 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV12(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV12(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV12(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV12(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV12(
            networkClient,
            EnvelopeProtoMapperImpl()
        )

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV12(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV12(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV12(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV12(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV12(networkClient)

    override val assetApi: AssetApi get() = AssetApiV12(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV12(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV12(networkClient)

    override val selfApi: SelfApi get() = SelfApiV12(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV12(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV12(networkClient)

    override val callApi: CallApi get() = CallApiV12(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV12(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV12(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV12(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV12(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV12(networkClient)

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV12(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV12(networkClient)

    override val cellsHttpClient: HttpClient = networkClient.httpClient

    override val remoteBackupApi: RemoteBackupApi
        get() = RemoteBackupApiV12(networkClient.httpClient, null)
}
