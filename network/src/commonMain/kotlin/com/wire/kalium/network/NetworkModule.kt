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
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientApiImp
import com.wire.kalium.network.api.user.login.LoginApi
import com.wire.kalium.network.api.user.login.LoginApiImp
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
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.host
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol

class NetworkModule(
    private val credentialsProvider: CredentialsProvider,
    private val engine: HttpClientEngine = defaultHttpEngine()
) {

    private val hostProvider = HostProvider

    val loginApi: LoginApi by lazy {
        LoginApiImp(anonymousHttpClient)
    }
    val authApi: AuthApi by lazy<AuthApi> {
        AuthApiImp(authenticatedHttpClient)
    }
    val logoutApi: LogoutApi by lazy {
        LogoutImp(authenticatedHttpClient)
    }
    val clientApi: ClientApi by lazy {
        ClientApiImp(authenticatedHttpClient)
    }
    val messageApi: MessageApi by lazy {
        MessageApiImp(authenticatedHttpClient)
    }
    val conversationApi: ConversationApi by lazy {
        ConversationApiImp(authenticatedHttpClient)
    }
    val preKeyApi: PreKeyApi by lazy {
        PreKeyApiImpl(authenticatedHttpClient)
    }
    val assetApi: AssetApi by lazy {
        AssetApiImp(authenticatedHttpClient)
    }
    val notificationApi: NotificationApi by lazy {
        NotificationApiImpl(authenticatedHttpClient)
    }

    private val kotlinxSerializer = KotlinxSerializer(KtxSerializer.json)

    private fun provideBaseHttpClient(config: HttpClientConfig<*>.() -> Unit = {}) = HttpClient(engine) {
        defaultRequest {
            header("Content-Type", "application/json")
            host = HostProvider.host
            url.protocol = URLProtocol.HTTPS
        }
        install(JsonFeature) {
            serializer = kotlinxSerializer
            accept(ContentType.Application.Json)
        }
    }

    internal val anonymousHttpClient by lazy {
        provideBaseHttpClient()
    }

    internal val authenticatedHttpClient by lazy {
        provideBaseHttpClient {
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
