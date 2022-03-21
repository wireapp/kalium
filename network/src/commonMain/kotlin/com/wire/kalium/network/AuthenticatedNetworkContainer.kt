package com.wire.kalium.network

import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.asset.AssetApi
import com.wire.kalium.network.api.asset.AssetApiImpl
import com.wire.kalium.network.api.auth.AccessTokenApiImpl
import com.wire.kalium.network.api.contact.search.ContactSearchApi
import com.wire.kalium.network.api.contact.search.ContactSearchApiImpl
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationApiImp
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
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsApiImp
import com.wire.kalium.network.api.user.logout.LogoutApi
import com.wire.kalium.network.api.user.logout.LogoutImpl
import com.wire.kalium.network.api.user.self.SelfApi
import com.wire.kalium.network.api.user.self.SelfApiImpl
import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer

class AuthenticatedNetworkContainer(
    private val backendConfig: BackendConfig,
    private val sessionDTO: SessionDTO,
    private val engine: HttpClientEngine = defaultHttpEngine(),
) {

    val logoutApi: LogoutApi get() = LogoutImpl(authenticatedHttpClient, sessionDTO.refreshToken)

    val clientApi: ClientApi get() = ClientApiImpl(authenticatedHttpClient)

    val messageApi: MessageApi get() = MessageApiImp(authenticatedHttpClient, provideEnvelopeProtoMapper())

    val conversationApi: ConversationApi get() = ConversationApiImp(authenticatedHttpClient)

    val preKeyApi: PreKeyApi get() = PreKeyApiImpl(authenticatedHttpClient)

    val assetApi: AssetApi get() = AssetApiImpl(authenticatedHttpClient)

    val notificationApi: NotificationApi get() = NotificationApiImpl(authenticatedHttpClient, backendConfig)

    val teamsApi: TeamsApi get() = TeamsApiImp(authenticatedHttpClient)

    val selfApi: SelfApi get() = SelfApiImpl(authenticatedHttpClient)

    val userDetailsApi: UserDetailsApi get() = UserDetailsApiImp(authenticatedHttpClient)

    val contactSearchApi: ContactSearchApi get() = ContactSearchApiImpl(authenticatedHttpClient)

    internal val authenticatedHttpClient by lazy {
        provideBaseHttpClient(engine, HttpClientOptions.DefaultHost(backendConfig)) {
            installAuth()
        }
    }

    private fun HttpClientConfig<*>.installAuth() {
        install(Auth) {
            bearer {
                // TODO: store the new token locally
                var access = sessionDTO.accessToken
                var refresh = sessionDTO.refreshToken
                loadTokens {
                    BearerTokens(accessToken = access, refreshToken = refresh)
                }
                refreshTokens {
                    val refreshedResponse = AccessTokenApiImpl(client).getToken(oldTokens!!.refreshToken)
                    return@refreshTokens if (refreshedResponse.isSuccessful()) {
                        refreshedResponse.value.first.let { access = it.value }
                        refreshedResponse.value.second?.let { refresh = it.value }
                        BearerTokens(access, refresh)
                    } else {
                        // TODO: if the refreshToken is expired logout ?
                        null
                    }
                }
            }
        }
    }
}
