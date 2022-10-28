package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
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
import com.wire.kalium.network.api.base.authenticated.serverpublickey.MLSPublicKeyApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.v0.authenticated.networkContainer.AuthenticatedNetworkContainerV0
import com.wire.kalium.network.api.v2.authenticated.networkContainer.AuthenticatedNetworkContainerV2
import com.wire.kalium.network.api.v3.authenticated.networkContainer.AuthenticatedNetworkContainerV3
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

    val mlsPublicKeyApi: MLSPublicKeyApi

    companion object {
        fun create(
            sessionManager: SessionManager
        ): AuthenticatedNetworkContainer {
            val version = sessionManager.session().second.metaData.commonApiVersion.version
            return when (version) {
                0 -> AuthenticatedNetworkContainerV0(
                    sessionManager
                )

                1 -> AuthenticatedNetworkContainerV0(
                    sessionManager
                )

                2 -> AuthenticatedNetworkContainerV2(
                    sessionManager
                )

                3 -> AuthenticatedNetworkContainerV3(
                    sessionManager
                )

                else -> throw error("Unsupported version: $version")
            }
        }
    }
}

internal interface AuthenticatedHttpClientProvider {
    val backendConfig: ServerConfigDTO.Links
    val networkClient: AuthenticatedNetworkClient
    val websocketClient: AuthenticatedWebSocketClient
    val networkClientWithoutCompression: AuthenticatedNetworkClient
}

internal class AuthenticatedHttpClientProviderImpl(
    private val sessionManager: SessionManager,
    private val engine: HttpClientEngine = defaultHttpEngine(),
) : AuthenticatedHttpClientProvider {
    override val backendConfig = sessionManager.session().second.links

    override val networkClient by lazy {
        AuthenticatedNetworkClient(
            engine,
            sessionManager
        )
    }
    override val websocketClient by lazy {
        AuthenticatedWebSocketClient(engine, sessionManager)
    }
    override val networkClientWithoutCompression by lazy {
        AuthenticatedNetworkClient(engine, sessionManager, installCompression = false)
    }
}
