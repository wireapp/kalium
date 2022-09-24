package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
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
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyApi
import com.wire.kalium.network.api.base.authenticated.search.UserSearchApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
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
