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

package com.wire.kalium.network.api.v0.authenticated.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.WildCardApi
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
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
import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import com.wire.kalium.network.api.v0.authenticated.AssetApiV0
import com.wire.kalium.network.api.v0.authenticated.CallApiV0
import com.wire.kalium.network.api.v0.authenticated.ClientApiV0
import com.wire.kalium.network.api.v0.authenticated.ConnectionApiV0
import com.wire.kalium.network.api.v0.authenticated.ConversationApiV0
import com.wire.kalium.network.api.v0.authenticated.E2EIApiV0
import com.wire.kalium.network.api.v0.authenticated.FeatureConfigApiV0
import com.wire.kalium.network.api.v0.authenticated.KeyPackageApiV0
import com.wire.kalium.network.api.v0.authenticated.LogoutApiV0
import com.wire.kalium.network.api.v0.authenticated.MLSMessageApiV0
import com.wire.kalium.network.api.v0.authenticated.MLSPublicKeyApiV0
import com.wire.kalium.network.api.v0.authenticated.MessageApiV0
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0
import com.wire.kalium.network.api.v0.authenticated.PreKeyApiV0
import com.wire.kalium.network.api.v0.authenticated.PropertiesApiV0
import com.wire.kalium.network.api.v0.authenticated.SelfApiV0
import com.wire.kalium.network.api.v0.authenticated.TeamsApiV0
import com.wire.kalium.network.api.v0.authenticated.UserDetailsApiV0
import com.wire.kalium.network.api.v0.authenticated.UserSearchApiV0
import com.wire.kalium.network.api.vcommon.WildCardApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.websocket.WebSocketSession

internal class AuthenticatedNetworkContainerV0 internal constructor(
    private val sessionManager: SessionManager,
    certificatePinning: CertificatePinning,
    mockEngine: HttpClientEngine?,
    mockWebSocketSession: WebSocketSession?,
    kaliumLogger: KaliumLogger,
    engine: HttpClientEngine = mockEngine ?: defaultHttpEngine(
        serverConfigDTOApiProxy = sessionManager.serverConfig().links.apiProxy,
        proxyCredentials = sessionManager.proxyCredentials(),
        certificatePinning = certificatePinning
    ),
) : AuthenticatedNetworkContainer,
    AuthenticatedHttpClientProvider by AuthenticatedHttpClientProviderImpl(
        sessionManager = sessionManager,
        accessTokenApi = { httpClient -> AccessTokenApiV0(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV0(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV0(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV0(networkClient)

    override val messageApi: MessageApi get() = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV0()

    override val e2eiApi: E2EIApi get() = E2EIApiV0()

    override val conversationApi: ConversationApi get() = ConversationApiV0(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV0()

    override val preKeyApi: PreKeyApi get() = PreKeyApiV0(networkClient)

    override val assetApi: AssetApi get() = AssetApiV0(networkClientWithoutCompression)

    override val notificationApi: NotificationApi get() = NotificationApiV0(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiV0(networkClient)

    override val selfApi: SelfApi get() = SelfApiV0(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV0(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV0(networkClient)

    override val callApi: CallApi get() = CallApiV0(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV0(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV0(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV0()

    override val propertiesApi: PropertiesApi get() = PropertiesApiV0(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)
}
