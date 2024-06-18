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

package com.wire.kalium.network.api.v4.authenticated.networkContainer

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
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v4.authenticated.AccessTokenApiV4
import com.wire.kalium.network.api.v4.authenticated.AssetApiV4
import com.wire.kalium.network.api.v4.authenticated.CallApiV4
import com.wire.kalium.network.api.v4.authenticated.ClientApiV4
import com.wire.kalium.network.api.v4.authenticated.ConnectionApiV4
import com.wire.kalium.network.api.v4.authenticated.ConversationApiV4
import com.wire.kalium.network.api.v4.authenticated.E2EIApiV4
import com.wire.kalium.network.api.v4.authenticated.FeatureConfigApiV4
import com.wire.kalium.network.api.v4.authenticated.KeyPackageApiV4
import com.wire.kalium.network.api.v4.authenticated.LogoutApiV4
import com.wire.kalium.network.api.v4.authenticated.MLSMessageApiV4
import com.wire.kalium.network.api.v4.authenticated.MLSPublicKeyApiV4
import com.wire.kalium.network.api.v4.authenticated.MessageApiV4
import com.wire.kalium.network.api.v4.authenticated.NotificationApiV4
import com.wire.kalium.network.api.v4.authenticated.PreKeyApiV4
import com.wire.kalium.network.api.v4.authenticated.PropertiesApiV4
import com.wire.kalium.network.api.v4.authenticated.SelfApiV4
import com.wire.kalium.network.api.v4.authenticated.TeamsApiV4
import com.wire.kalium.network.api.v4.authenticated.UserDetailsApiV4
import com.wire.kalium.network.api.v4.authenticated.UserSearchApiV4
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.CertificatePinning
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine

@Suppress("LongParameterList")
internal class AuthenticatedNetworkContainerV4 internal constructor(
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
        accessTokenApi = { httpClient -> AccessTokenApiV4(httpClient) },
        engine = engine,
        kaliumLogger = kaliumLogger
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV4(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV4(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV4(networkClient)

    override val messageApi: MessageApi get() = MessageApiV4(networkClient, EnvelopeProtoMapperImpl())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV4()

    override val e2eiApi: E2EIApi get() = E2EIApiV4()

    override val conversationApi: ConversationApi get() = ConversationApiV4(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV4()

    override val preKeyApi: PreKeyApi get() = PreKeyApiV4(networkClient)

    override val assetApi: AssetApi get() = AssetApiV4(networkClientWithoutCompression, selfUserId)

    override val notificationApi: NotificationApi get() = NotificationApiV4(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiV4(networkClient)

    override val selfApi: SelfApi get() = SelfApiV4(networkClient, sessionManager)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV4(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV4(networkClient)

    override val callApi: CallApi get() = CallApiV4(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV4(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV4(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV4()

    override val propertiesApi: PropertiesApi get() = PropertiesApiV4(networkClient)
}
