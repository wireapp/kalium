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
import com.wire.kalium.network.api.message.MessageApiImp
import com.wire.kalium.network.api.message.provideEnvelopeProtoMapper
import com.wire.kalium.network.api.notification.NotificationApi
import com.wire.kalium.network.api.notification.NotificationApiImpl
import com.wire.kalium.network.api.prekey.PreKeyApi
import com.wire.kalium.network.api.prekey.PreKeyApiImpl
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.teams.TeamsApiImp
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientApiImpl
import com.wire.kalium.network.api.user.connection.ConnectionApi
import com.wire.kalium.network.api.user.connection.ConnectionApiImpl
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsApiImp
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.network.api.user.logout.LogoutImpl
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.network.api.user.self.SelfApiImpl
import com.wire.kalium.network.serialization.mls
import com.wire.kalium.network.serialization.xprotobuf
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.session.installAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets

class AuthenticatedNetworkContainer(
    private val sessionManager: SessionManager,
    private val engine: HttpClientEngine = defaultHttpEngine(),
) {

    private val backendConfig = sessionManager.session().second

    val logoutApi: LogoutApi get() = LogoutImpl(authenticatedHttpClient, sessionManager)

    val clientApi: ClientApi get() = ClientApiImpl(authenticatedHttpClient)

    val messageApi: MessageApi get() = MessageApiImp(authenticatedHttpClient, provideEnvelopeProtoMapper())

    val mlsMessageApi: MLSMessageApi get() = MLSMessageApiImpl(authenticatedHttpClient)

    val conversationApi: ConversationApi get() = ConversationApiImpl(authenticatedHttpClient)

    val keyPackageApi: KeyPackageApi get() = KeyPackageApiImpl(authenticatedHttpClient)

    val preKeyApi: PreKeyApi get() = PreKeyApiImpl(authenticatedHttpClient)

    val assetApi: AssetApi get() = AssetApiImpl(authenticatedHttpClient)

    val notificationApi: NotificationApi get() = NotificationApiImpl(authenticatedHttpClient, webSocketClientProvider, backendConfig)

    val teamsApi: TeamsApi get() = TeamsApiImp(authenticatedHttpClient)

    val selfApi: SelfApi get() = SelfApiImpl(authenticatedHttpClient)

    val userDetailsApi: UserDetailsApi get() = UserDetailsApiImp(authenticatedHttpClient)

    val userSearchApi: UserSearchApi get() = UserSearchApiImpl(authenticatedHttpClient)

    val callApi: CallApi get() = CallApiImpl(authenticatedHttpClient)

    val connectionApi: ConnectionApi get() = ConnectionApiImpl(authenticatedHttpClient)

    /**
     * A [HttpClient] that contains all needed authentication headers and handles refresh of tokens.
     * @see [provideBaseHttpClient]
     */
    internal val authenticatedHttpClient by lazy { httpClient() }

    /**
     * A [WebSocketClientProvider] that provides a disposable [HttpClient] just for WebSockets.
     * Like [authenticatedHttpClient], it contains all needed authentication headers and handles refresh of tokens.
     */
    private val webSocketClientProvider: () -> HttpClient
        get() = {
            httpClient().config {
                install(WebSockets)
            }
        }

    private fun httpClient() = provideBaseHttpClient(engine, HttpClientOptions.DefaultHost(backendConfig)) {
        installAuth(sessionManager)
        install(ContentNegotiation) {
            mls()
            xprotobuf()
        }
    }
}
