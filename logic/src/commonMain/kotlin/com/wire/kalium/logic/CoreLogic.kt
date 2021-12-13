package com.wire.kalium.logic

import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthSessionMapper
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.LoginNetworkContainer

class CoreLogic(
    private val authSessionMapper: AuthSessionMapper
) {

    private val loginContainer: LoginNetworkContainer by lazy {
        LoginNetworkContainer()
    }

    private val userScopeStorage = hashMapOf<AuthSession, AuthenticatedDataSourceSet>()
    // TODO: - Update UserSession when token is refreshed
    //       - Delete UserSession and DataSourceSets when user logs-out

    val authenticationScope: AuthenticationScope get() = AuthenticationScope(loginContainer)

    suspend fun <T> authenticationScope(action: suspend AuthenticationScope.() -> T): T = authenticationScope.action()

    suspend fun <T> sessionScope(session: AuthSession, action: suspend UserSessionScope.() -> T): T {
        return getSessionScope(session).action()
    }

    fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(authSessionMapper.toSessionCredentials(session))
            AuthenticatedDataSourceSet(networkContainer).also {
                userScopeStorage[session] = it
            }
        }
        return UserSessionScope(session, dataSourceSet)
    }

}
