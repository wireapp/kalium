package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.network.AuthenticatedNetworkContainer

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String) :
    CoreLogicCommon(clientLabel, rootProteusDirectoryPath) {
    override fun getAuthenticationScope(): AuthenticationScope = AuthenticationScope(loginContainer, clientLabel)

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer =
                AuthenticatedNetworkContainer(sessionMapper.toSessionCredentials(session))
            val proteusClient = ProteusClient(rootProteusDirectoryPath, session.userId)
            AuthenticatedDataSourceSet(networkContainer, proteusClient).also {
                userScopeStorage[session] = it
            }
        }
        return UserSessionScope(dataSourceSet)
    }
}
