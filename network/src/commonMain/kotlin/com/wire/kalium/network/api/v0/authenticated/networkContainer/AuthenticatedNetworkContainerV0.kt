package com.wire.kalium.network.api.v0.authenticated.networkContainer

import com.wire.kalium.network.ServerMetaDataManager
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
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.v0.authenticated.AssetApiV0
import com.wire.kalium.network.api.v0.authenticated.CallApiV0
import com.wire.kalium.network.api.v0.authenticated.ClientApiV0
import com.wire.kalium.network.api.v0.authenticated.ConnectionApiV0
import com.wire.kalium.network.api.v0.authenticated.ConversationApiV0
import com.wire.kalium.network.api.v0.authenticated.FeatureConfigApiV0
import com.wire.kalium.network.api.v0.authenticated.KeyPackageApiV0
import com.wire.kalium.network.api.v0.authenticated.LogoutApiV0
import com.wire.kalium.network.api.v0.authenticated.MLSMessageApiV0
import com.wire.kalium.network.api.v0.authenticated.MessageApiV0
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0
import com.wire.kalium.network.api.v0.authenticated.PreKeyApiV0
import com.wire.kalium.network.api.v0.authenticated.SelfApiV0
import com.wire.kalium.network.api.v0.authenticated.TeamsApiV0
import com.wire.kalium.network.api.v0.authenticated.UserDetailsApiV0
import com.wire.kalium.network.api.v0.authenticated.UserSearchApiV0
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProvider
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine

class AuthenticatedNetworkContainerV0(
    private val sessionManager: SessionManager,
    serverMetaDataManager: ServerMetaDataManager,
    engine: HttpClientEngine = defaultHttpEngine(),
    developmentApiEnabled: Boolean = false
) : AuthenticatedNetworkContainer,
    AuthenticatedHttpClientProvider by AuthenticatedHttpClientProviderImpl(
        sessionManager,
        serverMetaDataManager,
        engine,
        developmentApiEnabled
    ) {

    override val logoutApi: LogoutApi get() = LogoutApiV0(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiV0(networkClient)

    override val messageApi: MessageApi get() = MessageApiV0(networkClient, provideEnvelopeProtoMapper())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiV0(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiV0(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiV0(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiV0(networkClient)

    override val assetApi: AssetApi get() = AssetApiV0(networkClientWithoutCompression)

    override val notificationApi: NotificationApi get() = NotificationApiV0(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiV0(networkClient)

    override val selfApi: SelfApi get() = SelfApiV0(networkClient)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiV0(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiV0(networkClient)

    override val callApi: CallApi get() = CallApiV0(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiV0(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiV0(networkClient)
}
