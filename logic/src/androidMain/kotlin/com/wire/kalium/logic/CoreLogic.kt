package com.wire.kalium.logic

import android.content.Context
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.network_config.BackendType
import com.wire.kalium.network.AuthenticatedNetworkContainer

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from CoreLogicCommon
 */
actual class CoreLogic(
    private val applicationContext: Context,
    clientLabel: String,
    rootProteusDirectoryPath: String,
    backEndType: BackendType
) : CoreLogicCommon(clientLabel, rootProteusDirectoryPath, backEndType) {
    override fun getAuthenticationScope(): AuthenticationScope = AuthenticationScope(loginContainer, clientLabel, applicationContext)

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(
                sessionCredentials = sessionMapper.toSessionCredentials(session),
                backEndConfig = backEndTypeMapper.toBackendConfig(backEndType)
            )
            val proteusClient = ProteusClient(rootProteusDirectoryPath, session.userId)
            AuthenticatedDataSourceSet(networkContainer, proteusClient).also {
                userScopeStorage[session] = it
            }
        }
        return UserSessionScope(applicationContext, dataSourceSet)
    }
}
