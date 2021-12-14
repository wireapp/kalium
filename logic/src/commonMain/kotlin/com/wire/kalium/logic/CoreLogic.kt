package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthSessionMapper
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer

class CoreLogic(
    private val clientLabel: String,
    private val rootProteusDirectoryPath: String,
    private val authSessionMapper: AuthSessionMapper = AuthSessionMapper()
) {

    private val loginContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    private val userScopeStorage = hashMapOf<AuthSession, AuthenticatedDataSourceSet>()
    // TODO: - Update UserSession when token is refreshed
    //       - Delete UserSession and DataSourceSets when user logs-out

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    fun getAuthenticationScope(): AuthenticationScope = AuthenticationScope(loginContainer, clientLabel)

    @Suppress("MemberVisibilityCanBePrivate") // Can be used by other targets like iOS and JS
    fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(authSessionMapper.toSessionCredentials(session))
            val proteusClient = ProteusClient(rootProteusDirectoryPath, session.userId)
            AuthenticatedDataSourceSet(networkContainer, proteusClient).also {
                userScopeStorage[session] = it
            }
        }
        return UserSessionScope(session, dataSourceSet)
    }

    suspend fun <T> authenticationScope(action: suspend AuthenticationScope.() -> T)
            : T = getAuthenticationScope().action()

    suspend fun <T> sessionScope(session: AuthSession, action: suspend UserSessionScope.() -> T)
            : T = getSessionScope(session).action()

}
