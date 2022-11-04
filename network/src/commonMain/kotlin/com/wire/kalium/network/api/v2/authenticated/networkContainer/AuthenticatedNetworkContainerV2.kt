package com.wire.kalium.network.api.v2.authenticated.networkContainer

import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.asset.AssetApi
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.connection.ConnectionApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.base.authenticated.keypackage.KeyPackageApi
import com.wire.kalium.network.api.base.authenticated.logout.LogoutApi
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.provideEnvelopeProtoMapper
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import com.wire.kalium.network.api.v2.authenticated.AccessTokenApiV2
import com.wire.kalium.network.api.v2.authenticated.AssetApiV2
import com.wire.kalium.network.api.v2.authenticated.CallApiV2
import com.wire.kalium.network.api.v2.authenticated.ClientApiV2
import com.wire.kalium.network.api.v2.authenticated.ConnectionApiV2
import com.wire.kalium.network.api.v2.authenticated.ConversationApiV2
import com.wire.kalium.network.api.v2.authenticated.FeatureConfigApiV2
import com.wire.kalium.network.api.v2.authenticated.KeyPackageApiV2
import com.wire.kalium.network.api.v2.authenticated.LogoutApiV2
import com.wire.kalium.network.api.v2.authenticated.MLSMessageApiV2
import com.wire.kalium.network.api.v2.authenticated.MLSPublicKeyApiV2
import com.wire.kalium.network.api.v2.authenticated.MessageApiV2
import com.wire.kalium.network.api.v2.authenticated.NotificationApiV2
import com.wire.kalium.network.api.v2.authenticated.PreKeyApiV2
import com.wire.kalium.network.api.v2.authenticated.SelfApiV2
import com.wire.kalium.network.api.v2.authenticated.TeamsApiV2
import com.wire.kalium.network.api.v2.authenticated.UserDetailsApiV2
import com.wire.kalium.network.api.v2.authenticated.UserSearchApiV2
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine

internal class AuthenticatedNetworkContainerV2 internal constructor(
    private val sessionManager: SessionManager,
    engine: HttpClientEngine = defaultHttpEngine(sessionManager.serverConfig().links.proxy, sessionManager.proxyCredentials())
) : AuthenticatedNetworkContainer,
    AuthenticatedHttpClientProvider by AuthenticatedHttpClientProviderImpl(
        sessionManager,
        { httpClient -> AccessTokenApiV2(httpClient) },
        engine
    ) {

    override val accessTokenApi: AccessTokenApi get() = AccessTokenApiV2(networkClient.httpClient)

    override val logoutApi: LogoutApi get() = LogoutApiV2(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV2(networkClient)

    override val messageApi: MessageApi get() = MessageApiV2(networkClient, provideEnvelopeProtoMapper())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV2(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV2(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV2(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV2(networkClient)

    override val assetApi: AssetApi get() = AssetApiV2(networkClientWithoutCompression)

    override val notificationApi: NotificationApi get() = NotificationApiV2(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiV2(networkClient)

    override val selfApi: SelfApi get() = SelfApiV2(networkClient)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV2(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV2(networkClient)

    override val callApi: CallApi get() = CallApiV2(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV2(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV2(networkClient)

    override val mlsPublicKeyApi: MLSPublicKeyApi get() = MLSPublicKeyApiV2(networkClient)
}
