package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.ServerMetaDataManager
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetApiImpl
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.network.api.call.CallApiImpl
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchApiImpl
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationApiImpl
import com.wire.kalium.network.api.featureConfigs.FeatureConfigApi
import com.wire.kalium.network.api.featureConfigs.FeatureConfigApiImpl
import com.wire.kalium.network.api.keypackage.KeyPackageApi
import com.wire.kalium.network.api.keypackage.KeyPackageApiImpl
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.api.message.MLSMessageApiImpl
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessageApiImpl
import com.wire.kalium.network.api.message.provideEnvelopeProtoMapper
import com.wire.kalium.network.api.notification.NotificationApi
import com.wire.kalium.network.api.notification.NotificationApiImpl
import com.wire.kalium.network.api.prekey.PreKeyApi
import com.wire.kalium.network.api.prekey.PreKeyApiImpl
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.teams.TeamsApiImpl
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientApiImpl
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionApiImpl
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsApiImpl
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.network.api.user.logout.LogoutImpl
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.network.api.user.self.SelfApiImpl
import com.wire.kalium.network.defaultHttpEngine
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.HttpClientEngine

interface AuthenticatedNetworkContainer {

    val logoutApi: LogoutApi

    val clientApi: ClientApi

    val messageApi: MessageApi

    val mlsMessageApi: MLSMessageApi

    val conversationApi: ConversationApi

    val keyPackageApi: KeyPackageApi

    val preKeyApi: PreKeyApi

    val assetApi: AssetApi

    val notificationApi: NotificationApi

    val teamsApi: TeamsApi

    val selfApi: SelfApi

    val userDetailsApi: UserDetailsApi

    val userSearchApi: UserSearchApi

    val callApi: CallApi

    val connectionApi: ConnectionApi

    val featureConfigApi: FeatureConfigApi
}

internal interface AuthenticatedHttpClientProvider {
    val backendConfig: ServerConfigDTO.Links
    val networkClient: AuthenticatedNetworkClient
    val websocketClient: AuthenticatedWebSocketClient
    val networkClientWithoutCompression: AuthenticatedNetworkClient
}

internal class AuthenticatedHttpClientProviderImpl(
    private val sessionManager: SessionManager,
    serverMetaDataManager: ServerMetaDataManager,
    private val engine: HttpClientEngine = defaultHttpEngine(),
    private val developmentApiEnabled: Boolean = false
) : AuthenticatedHttpClientProvider {
    override val backendConfig = sessionManager.session().second

    override val networkClient by lazy {
        AuthenticatedNetworkClient(engine, sessionManager, serverMetaDataManager, developmentApiEnabled = developmentApiEnabled)
    }
    override val websocketClient by lazy {
        AuthenticatedWebSocketClient(engine, sessionManager, serverMetaDataManager, developmentApiEnabled)
    }
    override val networkClientWithoutCompression by lazy {
        AuthenticatedNetworkClient(engine, sessionManager, serverMetaDataManager, false, developmentApiEnabled)
    }
}

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

    override val logoutApi: LogoutApi get() = LogoutImpl(networkClient, sessionManager)

    override val clientApi: ClientApi get() = ClientApiImpl(networkClient)

    override val messageApi: MessageApi get() = MessageApiImpl(networkClient, provideEnvelopeProtoMapper())

    override val mlsMessageApi: MLSMessageApi get() = MLSMessageApiImpl(networkClient)

    override val conversationApi: ConversationApi get() = ConversationApiImpl(networkClient)

    override val keyPackageApi: KeyPackageApi get() = KeyPackageApiImpl(networkClient)

    override val preKeyApi: PreKeyApi get() = PreKeyApiImpl(networkClient)

    override val assetApi: AssetApi get() = AssetApiImpl(networkClientWithoutCompression)

    override val notificationApi: NotificationApi get() = NotificationApiImpl(networkClient, websocketClient, backendConfig)

    override val teamsApi: TeamsApi get() = TeamsApiImpl(networkClient)

    override val selfApi: SelfApi get() = SelfApiImpl(networkClient)

    override val userDetailsApi: UserDetailsApi get() = UserDetailsApiImpl(networkClient)

    override val userSearchApi: UserSearchApi get() = UserSearchApiImpl(networkClient)

    override val callApi: CallApi get() = CallApiImpl(networkClient)

    override val connectionApi: ConnectionApi get() = ConnectionApiImpl(networkClient)

    override val featureConfigApi: FeatureConfigApi get() = FeatureConfigApiImpl(networkClient)
}
