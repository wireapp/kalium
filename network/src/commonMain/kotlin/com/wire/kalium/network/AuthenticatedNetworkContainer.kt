package com.wire.kalium.network

import com.wire.kalium.network.api.SessionCredentials
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
import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.URLProtocol

class AuthenticatedNetworkContainer(
    private val backendConfig: BackendConfig,
    private val sessionCredentials: SessionCredentials,
    private val engine: HttpClientEngine = defaultHttpEngine(),
    private val isRequestLoggingEnabled: Boolean = false
//    private val onTokenUpdate: (newTokenInfo: Pair<String, String>) -> Unit // Idea to let the network handle the refresh token automatically
) {
    private val authApi: AuthApi get() = AuthApiImp(authenticatedHttpClient)

    val logoutApi: LogoutApi get() = LogoutImp(authenticatedHttpClient)

    val clientApi: ClientApi get() = ClientApiImp(authenticatedHttpClient)

    val messageApi: MessageApi get() = MessageApiImp(authenticatedHttpClient)

    val conversationApi: ConversationApi get() = ConversationApiImp(authenticatedHttpClient)

    val preKeyApi: PreKeyApi get() = PreKeyApiImpl(authenticatedHttpClient)

    val assetApi: AssetApi get() = AssetApiImp(authenticatedHttpClient)

    val notificationApi: NotificationApi get() = NotificationApiImpl(authenticatedHttpClient)

    val teamsApi: TeamsApi get() = TeamsApiImp(authenticatedHttpClient)

    internal val authenticatedHttpClient by lazy {
        provideBaseHttpClient(engine, isRequestLoggingEnabled, HttpClientOptions.DefaultHost(backendConfig)) {
            installAuth()
        }
    }

    private val webSocketClient by lazy {
        HttpClient(engine) {
            defaultRequest {
                host = backendConfig.webSocketUrl
                url.protocol = URLProtocol.WSS
            }
            install(WebSockets)
            installAuth()
        }
    }

    private fun HttpClientConfig<*>.installAuth() {
        Auth {
            bearer {
                loadTokens {
                    BearerTokens(
                        accessToken = sessionCredentials.accessToken,
                        refreshToken = sessionCredentials.refreshToken
                    )
                }
                refreshTokens {
                    val refreshedResponse = authApi.renewAccessToken(sessionCredentials.refreshToken)

                    return@refreshTokens if (refreshedResponse.isSuccessful()) {
                        BearerTokens(refreshedResponse.value.accessToken, TODO("Get the 🍪"))
                    } else {
                        null
                    }
                }
            }
        }
    }
}
