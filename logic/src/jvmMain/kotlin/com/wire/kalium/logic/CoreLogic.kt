package com.wire.kalium.logic

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

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String) :
    CoreLogicCommon(
        clientLabel = clientLabel,
        rootProteusDirectoryPath = rootProteusDirectoryPath
    ) {
    override fun getAuthenticationScope(): AuthenticationScope {
        return AuthenticationScope(".", clientLabel)
    }

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(
                sessionDTO = sessionMapper.toSessionDTO(session),
                backendConfig = serverConfigMapper.toBackendConfig(serverConfig = session.serverConfig)
            )

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, session.userId)
            runBlocking { proteusClient.open() }


            val workScheduler = WorkScheduler(this, session)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder = EncryptedSettingsHolder("${PREFERENCE_FILE_PREFIX}-${session.userId}")
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val database = Database()

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

        return UserSessionScope(session, dataSourceSet)
    }

    private companion object {
        private const val PREFERENCE_FILE_PREFIX = "user-pref"
    }

}
