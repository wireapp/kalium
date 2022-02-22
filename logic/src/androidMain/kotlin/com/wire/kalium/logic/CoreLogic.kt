package com.wire.kalium.logic

import android.content.Context
import com.russhwolf.settings.BuildConfig
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import kotlinx.coroutines.runBlocking

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from CoreLogicCommon
 */
actual class CoreLogic(
    private val applicationContext: Context,
    clientLabel: String,
    rootProteusDirectoryPath: String,
) : CoreLogicCommon(clientLabel, rootProteusDirectoryPath) {
    override fun getAuthenticationScope(): AuthenticationScope =
        AuthenticationScope(clientLabel = clientLabel, applicationContext = applicationContext)

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(
                sessionDTO = sessionMapper.toSessionDTO(session),
                backendConfig = serverConfigMapper.toBackendConfig(serverConfig = session.serverConfig),
                isRequestLoggingEnabled = BuildConfig.DEBUG //TODO: Multi-platform logging solution!
            )

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, session.userId)
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(applicationContext, session)
            val syncManager = SyncManagerImpl(workScheduler)
            AuthenticatedDataSourceSet(networkContainer, proteusClient, workScheduler, syncManager).also {
                userScopeStorage[session] = it
            }
        }
        return UserSessionScope(applicationContext, session, dataSourceSet)
    }
}
