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

package com.wire.kalium.network.api.v5.authenticated.networkContainer

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
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v5.authenticated.AccessTokenApiV5
import com.wire.kalium.network.api.v5.authenticated.AssetApiV5
import com.wire.kalium.network.api.v5.authenticated.CallApiV5
import com.wire.kalium.network.api.v5.authenticated.ClientApiV5
import com.wire.kalium.network.api.v5.authenticated.ConnectionApiV5
import com.wire.kalium.network.api.v5.authenticated.ConversationApiV5
import com.wire.kalium.network.api.v5.authenticated.E2EIApiV5
import com.wire.kalium.network.api.v5.authenticated.FeatureConfigApiV5
import com.wire.kalium.network.api.v5.authenticated.KeyPackageApiV5
import com.wire.kalium.network.api.v5.authenticated.LogoutApiV5
import com.wire.kalium.network.api.v5.authenticated.MLSMessageApiV5
import com.wire.kalium.network.api.v5.authenticated.MLSPublicKeyApiV5
import com.wire.kalium.network.api.v5.authenticated.MessageApiV5
import com.wire.kalium.network.api.v5.authenticated.NotificationApiV5
import com.wire.kalium.network.api.v5.authenticated.PreKeyApiV5
import com.wire.kalium.network.api.v5.authenticated.PropertiesApiV5
import com.wire.kalium.network.api.v5.authenticated.SelfApiV5
import com.wire.kalium.network.api.v5.authenticated.TeamsApiV5
import com.wire.kalium.network.api.v5.authenticated.UserDetailsApiV5
import com.wire.kalium.network.api.v5.authenticated.UserSearchApiV5
import com.wire.kalium.network.api.vcommon.WildCardApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.websocket.WebSocketSession

@Suppress("LongParameterList")
internal class AuthenticatedNetworkContainerV5 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV5(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV5(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV5(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV5(networkClient)

    override val messageApi: MessageApi get() = MessageApiV5(networkClient, EnvelopeProtoMapperImpl())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV5(networkClient)

    override val e2eiApi: E2EIApi get() = E2EIApiV5(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV5(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV5(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV5(networkClient)

    override val assetApi: AssetApi get() = AssetApiV5(networkClientWithoutCompression, selfUserId)

    override val notificationApi: NotificationApi get() = NotificationApiV5(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiV5(networkClient)

    override val selfApi: SelfApi get() = SelfApiV5(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV5(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV5(networkClient)

    override val callApi: CallApi get() = CallApiV5(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV5(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV5(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV5(networkClient)

    override val propertiesApi: PropertiesApi get() = PropertiesApiV5(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)
}
