package com.wire.kalium.network

import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetApiImpl
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.network.api.call.CallApiImpl
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchApiImpl
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationApiImpl
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
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.HttpClientEngine

class AuthenticatedNetworkContainer(
    private val sessionManager: SessionManager,
    serverMetaDataManager: ServerMetaDataManager,
    private val engine: HttpClientEngine = defaultHttpEngine()
) {

    private val backendConfig = sessionManager.session().second

    internal val networkClient by lazy {
        AuthenticatedNetworkClient(engine, sessionManager, serverMetaDataManager)
    }
    internal val websocketClient by lazy {
        AuthenticatedWebSocketClient(engine, sessionManager, serverMetaDataManager)
    }

    val logoutApi: LogoutApi get() = LogoutImpl(networkClient, sessionManager)

    val clientApi: ClientApi get() = ClientApiImpl(networkClient)

    val messageApi: MessageApi get() = MessageApiImpl(networkClient, provideEnvelopeProtoMapper())

    val mlsMessageApi: MLSMessageApi get() = MLSMessageApiImpl(networkClient)

    val conversationApi: ConversationApi get() = ConversationApiImpl(networkClient)

    val keyPackageApi: KeyPackageApi get() = KeyPackageApiImpl(networkClient)

    val preKeyApi: PreKeyApi get() = PreKeyApiImpl(networkClient)

    val assetApi: AssetApi get() = AssetApiImpl(networkClient)

    val notificationApi: NotificationApi get() = NotificationApiImpl(networkClient, websocketClient, backendConfig)

    val teamsApi: TeamsApi get() = TeamsApiImpl(networkClient)

    val selfApi: SelfApi get() = SelfApiImpl(networkClient)

    val userDetailsApi: UserDetailsApi get() = UserDetailsApiImpl(networkClient)

    val userSearchApi: UserSearchApi get() = UserSearchApiImpl(networkClient)

    val callApi: CallApi get() = CallApiImpl(networkClient)

    val connectionApi: ConnectionApi get() = ConnectionApiImpl(networkClient)

}
