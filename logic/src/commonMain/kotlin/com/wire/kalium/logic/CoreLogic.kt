package com.wire.kalium.logic

import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer

class CoreLogic {

    private val loginContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    private val userScopeStorage = hashMapOf<AuthSession, AuthenticatedDataSourceSet>()
    // TODO: - Update UserSession when token is refreshed
    //       - Delete UserSession and DataSourceSets when user logs-out

    private val authenticationScope: AuthenticationScope get() = AuthenticationScope(loginContainer)

    suspend fun <T> authenticationScope(action: suspend AuthenticationScope.() -> T): T = authenticationScope.action()

    suspend fun <T> sessionScope(session: AuthSession, action: suspend UserSessionScope.() -> T): T {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(session)
            AuthenticatedDataSourceSet(networkContainer).also {
                userScopeStorage[session] = it
            }
        }
        return UserSessionScope(session, dataSourceSet.authenticatedNetworkContainer).action()
    }

}
