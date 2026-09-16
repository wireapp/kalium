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

package com.wire.kalium.network.api.v18.authenticated.networkContainer

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
import com.wire.kalium.network.api.v18.authenticated.AccessTokenApiV18
import com.wire.kalium.network.api.v18.authenticated.AssetApiV18
import com.wire.kalium.network.api.v18.authenticated.CallApiV18
import com.wire.kalium.network.api.v18.authenticated.ClientApiV18
import com.wire.kalium.network.api.v18.authenticated.ConnectionApiV18
import com.wire.kalium.network.api.v18.authenticated.ConversationApiV18
import com.wire.kalium.network.api.v18.authenticated.ConversationHistoryApiV18
import com.wire.kalium.network.api.v18.authenticated.E2EIApiV18
import com.wire.kalium.network.api.v18.authenticated.FeatureConfigApiV18
import com.wire.kalium.network.api.v18.authenticated.KeyPackageApiV18
import com.wire.kalium.network.api.v18.authenticated.LogoutApiV18
import com.wire.kalium.network.api.v18.authenticated.MLSMessageApiV18
import com.wire.kalium.network.api.v18.authenticated.MLSPublicKeyApiV18
import com.wire.kalium.network.api.v18.authenticated.MessageApiV18
import com.wire.kalium.network.api.v0.authenticated.NomadDeviceSyncApiV0
import com.wire.kalium.network.api.v18.authenticated.MeetingApiV18
import com.wire.kalium.network.api.v18.authenticated.NotificationApiV18
import com.wire.kalium.network.api.v18.authenticated.PreKeyApiV18
import com.wire.kalium.network.api.v18.authenticated.PropertiesApiV18
import com.wire.kalium.network.api.v18.authenticated.SelfApiV18
import com.wire.kalium.network.api.v18.authenticated.ServerTimeApiV18
import com.wire.kalium.network.api.v18.authenticated.TeamsApiV18
import com.wire.kalium.network.api.v18.authenticated.UpgradePersonalToTeamApiV18
import com.wire.kalium.network.api.v18.authenticated.UserDetailsApiV18
import com.wire.kalium.network.api.v18.authenticated.UserSearchApiV18
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
internal class AuthenticatedNetworkContainerV18 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV18(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV18(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV18(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV18(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV18(
            networkClient,
            EnvelopeProtoMapperImpl()
        )
    override val nomadDeviceSyncApi: NomadDeviceSyncApi get() = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl)

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV18(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV18(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV18(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV18(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV18(networkClient)

    override val assetApi: AssetApi get() = AssetApiV18(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV18(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV18(networkClient)

    override val selfApi: SelfApi get() = SelfApiV18(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV18(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV18(networkClient)

    override val callApi: CallApi get() = CallApiV18(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV18(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV18(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV18(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV18(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV18(networkClient)

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV18(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV18(networkClient)

    override val meetingApi: MeetingApi get() = MeetingApiV18(networkClient)

    override val cellsHttpClient: HttpClient = networkClient.httpClient
}
