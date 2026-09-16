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

package com.wire.kalium.network.api.v17.authenticated.networkContainer

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
import com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapperImpl
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v17.authenticated.AccessTokenApiV17
import com.wire.kalium.network.api.v17.authenticated.AssetApiV17
import com.wire.kalium.network.api.v17.authenticated.CallApiV17
import com.wire.kalium.network.api.v17.authenticated.ClientApiV17
import com.wire.kalium.network.api.v17.authenticated.ConnectionApiV17
import com.wire.kalium.network.api.v17.authenticated.ConversationApiV17
import com.wire.kalium.network.api.v17.authenticated.ConversationHistoryApiV17
import com.wire.kalium.network.api.v17.authenticated.E2EIApiV17
import com.wire.kalium.network.api.v17.authenticated.FeatureConfigApiV17
import com.wire.kalium.network.api.v17.authenticated.KeyPackageApiV17
import com.wire.kalium.network.api.v17.authenticated.LogoutApiV17
import com.wire.kalium.network.api.v17.authenticated.MLSMessageApiV17
import com.wire.kalium.network.api.v17.authenticated.MLSPublicKeyApiV17
import com.wire.kalium.network.api.v17.authenticated.MessageApiV17
import com.wire.kalium.network.api.v0.authenticated.NomadDeviceSyncApiV0
import com.wire.kalium.network.api.v17.authenticated.MeetingApiV17
import com.wire.kalium.network.api.v17.authenticated.NotificationApiV17
import com.wire.kalium.network.api.v17.authenticated.PreKeyApiV17
import com.wire.kalium.network.api.v17.authenticated.PropertiesApiV17
import com.wire.kalium.network.api.v17.authenticated.SelfApiV17
import com.wire.kalium.network.api.v17.authenticated.ServerTimeApiV17
import com.wire.kalium.network.api.v17.authenticated.TeamsApiV17
import com.wire.kalium.network.api.v17.authenticated.UpgradePersonalToTeamApiV17
import com.wire.kalium.network.api.v17.authenticated.UserDetailsApiV17
import com.wire.kalium.network.api.v17.authenticated.UserSearchApiV17
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
internal class AuthenticatedNetworkContainerV17 internal constructor(
    private val sessionManager: SessionManager,
    nomadServiceUrl: String? = null,
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
        nomadServiceUrl = nomadServiceUrl,
        accessTokenApi = { httpClient -> AccessTokenApiV17(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV17(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV17(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV17(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV17(
            networkClient,
            EnvelopeProtoMapperImpl()
        )
    override val nomadDeviceSyncApi: NomadDeviceSyncApi get() = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl)

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV17(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV17(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV17(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV17(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV17(networkClient)

    override val assetApi: AssetApi get() = AssetApiV17(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV17(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV17(networkClient)

    override val selfApi: SelfApi get() = SelfApiV17(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV17(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV17(networkClient)

    override val callApi: CallApi get() = CallApiV17(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV17(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV17(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV17(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV17(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV17(networkClient)

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV17(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV17(networkClient)

    override val meetingApi: MeetingApi get() = MeetingApiV17(networkClient)

    override val cellsHttpClient: HttpClient = networkClient.httpClient
}
