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

package com.wire.kalium.network.api.v16.authenticated.networkContainer

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
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v16.authenticated.AccessTokenApiV16
import com.wire.kalium.network.api.v16.authenticated.AssetApiV16
import com.wire.kalium.network.api.v16.authenticated.CallApiV16
import com.wire.kalium.network.api.v16.authenticated.ClientApiV16
import com.wire.kalium.network.api.v16.authenticated.ConnectionApiV16
import com.wire.kalium.network.api.v16.authenticated.ConversationApiV16
import com.wire.kalium.network.api.v16.authenticated.ConversationHistoryApiV16
import com.wire.kalium.network.api.v16.authenticated.E2EIApiV16
import com.wire.kalium.network.api.v16.authenticated.FeatureConfigApiV16
import com.wire.kalium.network.api.v16.authenticated.KeyPackageApiV16
import com.wire.kalium.network.api.v16.authenticated.LogoutApiV16
import com.wire.kalium.network.api.v16.authenticated.MLSMessageApiV16
import com.wire.kalium.network.api.v16.authenticated.MLSPublicKeyApiV16
import com.wire.kalium.network.api.v16.authenticated.MessageApiV16
import com.wire.kalium.network.api.v0.authenticated.NomadDeviceSyncApiV0
import com.wire.kalium.network.api.v16.authenticated.NotificationApiV16
import com.wire.kalium.network.api.v16.authenticated.PreKeyApiV16
import com.wire.kalium.network.api.v16.authenticated.PropertiesApiV16
import com.wire.kalium.network.api.v16.authenticated.SelfApiV16
import com.wire.kalium.network.api.v16.authenticated.ServerTimeApiV16
import com.wire.kalium.network.api.v16.authenticated.TeamsApiV16
import com.wire.kalium.network.api.v16.authenticated.UpgradePersonalToTeamApiV16
import com.wire.kalium.network.api.v16.authenticated.UserDetailsApiV16
import com.wire.kalium.network.api.v16.authenticated.UserSearchApiV16
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
internal class AuthenticatedNetworkContainerV16 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV16(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV16(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV16(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV16(networkClient)

    override val messageApi: MessageApi
        get() = MessageApiV16(
            networkClient,
            EnvelopeProtoMapperImpl()
        )
    override val nomadDeviceSyncApi: NomadDeviceSyncApi get() = NomadDeviceSyncApiV0(networkClient, nomadServiceUrl)

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV16(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV16(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV16(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV16(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV16(networkClient)

    override val assetApi: AssetApi get() = AssetApiV16(networkClientWithoutCompression, selfUserId)

    // It is important that this is lazy, since we need a single instance of the websocket client
    override val notificationApi: NotificationApi by lazy {
        NotificationApiV16(
            networkClient,
            websocketClient,
            backendConfig
        )
    }

    override val teamsApi: TeamsApi get() = TeamsApiV16(networkClient)

    override val selfApi: SelfApi get() = SelfApiV16(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV16(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV16(networkClient)

    override val callApi: CallApi get() = CallApiV16(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV16(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV16(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV16(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV16(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)

    override val conversationHistoryApi: ConversationHistoryApi get() = ConversationHistoryApiV16(networkClient)

    override val upgradePersonalToTeamApi: UpgradePersonalToTeamApi
        get() = UpgradePersonalToTeamApiV16(
            networkClient
        )

    override val serverTimeApi: ServerTimeApi
        get() = ServerTimeApiV16(networkClient)

    override val cellsHttpClient: HttpClient = networkClient.httpClient
}
