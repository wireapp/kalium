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

package com.wire.kalium.network.api.v3.authenticated.networkContainer

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
import com.wire.kalium.network.api.model.ApiModelMapperImpl
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v3.authenticated.AccessTokenApiV3
import com.wire.kalium.network.api.v3.authenticated.AssetApiV3
import com.wire.kalium.network.api.v3.authenticated.CallApiV3
import com.wire.kalium.network.api.v3.authenticated.ClientApiV3
import com.wire.kalium.network.api.v3.authenticated.ConnectionApiV3
import com.wire.kalium.network.api.v3.authenticated.ConversationApiV3
import com.wire.kalium.network.api.v3.authenticated.E2EIApiV3
import com.wire.kalium.network.api.v3.authenticated.FeatureConfigApiV3
import com.wire.kalium.network.api.v3.authenticated.KeyPackageApiV3
import com.wire.kalium.network.api.v3.authenticated.LogoutApiV3
import com.wire.kalium.network.api.v3.authenticated.MLSMessageApiV3
import com.wire.kalium.network.api.v3.authenticated.MLSPublicKeyApiV3
import com.wire.kalium.network.api.v3.authenticated.MessageApiV3
import com.wire.kalium.network.api.v3.authenticated.NotificationApiV3
import com.wire.kalium.network.api.v3.authenticated.PreKeyApiV3
import com.wire.kalium.network.api.v3.authenticated.PropertiesApiV3
import com.wire.kalium.network.api.v3.authenticated.SelfApiV3
import com.wire.kalium.network.api.v3.authenticated.TeamsApiV3
import com.wire.kalium.network.api.v3.authenticated.UserDetailsApiV3
import com.wire.kalium.network.api.v3.authenticated.UserSearchApiV3
import com.wire.kalium.network.api.v_common.WildCardApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.websocket.WebSocketSession

@Suppress("LongParameterList")
internal class AuthenticatedNetworkContainerV3 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV3(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger,
        webSocketSessionProvider = if (mockWebSocketSession != null) {
            { _, _ -> mockWebSocketSession }
        } else {
            null
        }
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV3(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV3(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV3(networkClient)

    override val messageApi: MessageApi get() = MessageApiV3(networkClient, EnvelopeProtoMapperImpl())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV3()

    override val e2eiApi: E2EIApi get() = E2EIApiV3()

    override val conversationApi: ConversationApi get() = ConversationApiV3(networkClient, ApiModelMapperImpl())

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV3()

    override val preKeyApi: PreKeyApi get() = PreKeyApiV3(networkClient)

    override val assetApi: AssetApi get() = AssetApiV3(networkClientWithoutCompression, selfUserId)

    override val notificationApi: NotificationApi get() = NotificationApiV3(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiV3(networkClient)

    override val selfApi: SelfApi get() = SelfApiV3(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV3(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV3(networkClient)

    override val callApi: CallApi get() = CallApiV3(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV3(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV3(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV3()

    override val propertiesApi: PropertiesApi get() = PropertiesApiV3(networkClient)

    override val wildCardApi: WildCardApi get() = WildCardApiImpl(networkClient)
}
