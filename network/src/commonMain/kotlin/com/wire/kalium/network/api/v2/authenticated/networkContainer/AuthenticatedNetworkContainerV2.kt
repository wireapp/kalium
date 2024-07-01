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

package com.wire.kalium.network.api.v2.authenticated.networkContainer

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.base.authenticated.TeamsApi
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
import com.wire.kalium.network.api.v2.authenticated.AccessTokenApiV2
import com.wire.kalium.network.api.v2.authenticated.AssetApiV2
import com.wire.kalium.network.api.v2.authenticated.CallApiV2
import com.wire.kalium.network.api.v2.authenticated.ClientApiV2
import com.wire.kalium.network.api.v2.authenticated.ConnectionApiV2
import com.wire.kalium.network.api.v2.authenticated.ConversationApiV2
import com.wire.kalium.network.api.v2.authenticated.E2EIApiV2
import com.wire.kalium.network.api.v2.authenticated.FeatureConfigApiV2
import com.wire.kalium.network.api.v2.authenticated.KeyPackageApiV2
import com.wire.kalium.network.api.v2.authenticated.LogoutApiV2
import com.wire.kalium.network.api.v2.authenticated.MLSMessageApiV2
import com.wire.kalium.network.api.v2.authenticated.MLSPublicKeyApiV2
import com.wire.kalium.network.api.v2.authenticated.MessageApiV2
import com.wire.kalium.network.api.v2.authenticated.NotificationApiV2
import com.wire.kalium.network.api.v2.authenticated.PreKeyApiV2
import com.wire.kalium.network.api.v2.authenticated.PropertiesApiV2
import com.wire.kalium.network.api.v2.authenticated.SelfApiV2
import com.wire.kalium.network.api.v2.authenticated.TeamsApiV2
import com.wire.kalium.network.api.v2.authenticated.UserDetailsApiV2
import com.wire.kalium.network.api.v2.authenticated.UserSearchApiV2
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine

@Suppress("LongParameterList")
internal class AuthenticatedNetworkContainerV2 internal constructor(
    private val sessionManager: SessionManager,
    private val selfUserId: UserId,
    certificatePinning: CertificatePinning,
    mockEngine: HttpClientEngine?,
    kaliumLogger: KaliumLogger,
    engine: HttpClientEngine = mockEngine ?: defaultHttpEngine(
        serverConfigDTOApiProxy = sessionManager.serverConfig().links.apiProxy,
        proxyCredentials = sessionManager.proxyCredentials(),
        certificatePinning = certificatePinning
    )
) : AuthenticatedNetworkContainer,
    AuthenticatedHttpClientProvider by AuthenticatedHttpClientProviderImpl(
        sessionManager = sessionManager,
        accessTokenApi = { httpClient -> AccessTokenApiV2(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV2(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV2(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV2(networkClient)

    override val messageApi: MessageApi get() = MessageApiV2(networkClient, EnvelopeProtoMapperImpl())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV2()

    override val e2eiApi: E2EIApi get() = E2EIApiV2()

    override val conversationApi: ConversationApi get() = ConversationApiV2(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV2()

    override val preKeyApi: PreKeyApi get() = PreKeyApiV2(networkClient)

    override val assetApi: AssetApi get() = AssetApiV2(networkClientWithoutCompression, selfUserId)

    override val notificationApi: NotificationApi get() = NotificationApiV2(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiV2(networkClient)

    override val selfApi: SelfApi get() = SelfApiV2(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV2(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV2(networkClient)

    override val callApi: CallApi get() = CallApiV2(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV2(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV2(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV2()

    override val propertiesApi: PropertiesApi get() = PropertiesApiV2(networkClient)
}
