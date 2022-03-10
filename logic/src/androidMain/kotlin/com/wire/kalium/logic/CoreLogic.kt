package com.wire.kalium.logic

import android.content.Context
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import kotlinx.coroutines.runBlocking

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from CoreLogicCommon
 */
actual class CoreLogic(
    private val applicationContext: Context,
    clientLabel: String,
    rootProteusDirectoryPath: String
) : CoreLogicCommon(clientLabel, rootProteusDirectoryPath) {

    override fun getAuthenticationScope(): AuthenticationScope =
        AuthenticationScope(
            clientLabel = clientLabel,
            applicationContext = applicationContext
        )

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(
                sessionDTO = sessionMapper.toSessionDTO(session),
                backendConfig = serverConfigMapper.toBackendConfig(serverConfig = session.serverConfig)
            )

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, session.userId)
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(applicationContext, session)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder = EncryptedSettingsHolder(applicationContext, "${PREFERENCE_FILE_PREFIX}-${session.userId}")
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val database = Database(applicationContext, "main.db", userPreferencesSettings)
            AuthenticatedDataSourceSet(
                networkContainer,
                proteusClient,
                workScheduler,
                syncManager,
                database,
                userPreferencesSettings,
                encryptedSettingsHolder
            ).also {
                userScopeStorage[session] = it
            }
        }
        return UserSessionScope(applicationContext, session, dataSourceSet)
    }

    private companion object {
        private const val PREFERENCE_FILE_PREFIX = "user-pref"
    }
}
