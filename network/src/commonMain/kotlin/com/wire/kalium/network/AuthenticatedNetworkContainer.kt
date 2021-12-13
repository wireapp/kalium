package com.wire.kalium.network

import com.wire.kalium.network.api.CredentialsProvider
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetApiImp
import com.wire.kalium.network.api.auth.AuthApi
import com.wire.kalium.network.api.auth.AuthApiImp
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationApiImp
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessageApiImp
import com.wire.kalium.network.api.notification.NotificationApi
import com.wire.kalium.network.api.notification.NotificationApiImpl
import com.wire.kalium.network.api.prekey.PreKeyApi
import com.wire.kalium.network.api.prekey.PreKeyApiImpl
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.teams.TeamsApiImp
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientApiImp
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.network.api.user.logout.LogoutImp
import com.wire.kalium.network.tools.HostProvider
import com.wire.kalium.network.tools.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.BearerTokens
import io.ktor.client.features.auth.providers.bearer
import io.ktor.client.features.defaultRequest
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.request.host
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLProtocol

class AuthenticatedNetworkContainer(
    private val credentialsProvider: CredentialsProvider,
    private val engine: HttpClientEngine = defaultHttpEngine(),
    private val isRequestLoggingEnabled: Boolean = false,
//    private val onTokenUpdate: (newTokenInfo: Pair<String,String>) -> Unit Idea to let the network handle the refresh token automatically
) {

    private val hostProvider = HostProvider

    private val authApi: AuthApi get() = AuthApiImp(authenticatedHttpClient)

    val logoutApi: LogoutApi get() = LogoutImp(authenticatedHttpClient)

    val clientApi: ClientApi get() = ClientApiImp(authenticatedHttpClient)

    val messageApi: MessageApi get() = MessageApiImp(authenticatedHttpClient)

    val conversationApi: ConversationApi get() = ConversationApiImp(authenticatedHttpClient)

    val preKeyApi: PreKeyApi get() = PreKeyApiImpl(authenticatedHttpClient)

    val assetApi: AssetApi get() = AssetApiImp(authenticatedHttpClient)

    val notificationApi: NotificationApi get() = NotificationApiImpl(authenticatedHttpClient)

    val teamsApi: TeamsApi get() = TeamsApiImp(authenticatedHttpClient)

    private val kotlinxSerializer = KotlinxSerializer(KtxSerializer.json)

    internal val authenticatedHttpClient by lazy {
        provideBaseHttpClient(kotlinxSerializer, engine, isRequestLoggingEnabled) {
            installAuth()
        }
    }

    private val webSocketClient by lazy {
        HttpClient(engine) {
            defaultRequest {
                host = hostProvider.host
                url.protocol = URLProtocol.WSS
            }
            install(WebSockets)
            installAuth()
        }
    }

    private fun HttpClientConfig<*>.installAuth() {
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(
                        accessToken = credentialsProvider.accessToken(),
                        refreshToken = credentialsProvider.refreshToken()
                    )
                }
                refreshTokens { unauthorizedResponse: HttpResponse ->
                    TODO("refresh the tokens, interface?")
                }
            }
        }
    }
}
