package com.wire.kalium.logic

import android.content.Context
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.local.SessionLocalDataSource
import com.wire.kalium.logic.data.session.local.SessionLocalRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import kotlinx.coroutines.runBlocking

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from CoreLogicCommon
 */
actual class CoreLogic(
    private val appContext: Context,
    clientLabel: String,
    rootProteusDirectoryPath: String
) : CoreLogicCommon(clientLabel, rootProteusDirectoryPath) {

    override fun getSessionRepo(): SessionRepository {
        val sessionPreferences =
            KaliumPreferencesSettings(EncryptedSettingsHolder(appContext, SHARED_PREFERENCE_FILE_NAME).encryptedSettings)
        val sessionStorage: SessionStorage = SessionStorageImpl(sessionPreferences)
        val sessionLocalRepository: SessionLocalRepository = SessionLocalDataSource(sessionStorage)
        return SessionDataSource(sessionLocalRepository)
    }

    override fun getAuthenticationScope(): AuthenticationScope =
        AuthenticationScope(clientLabel, sessionRepository, appContext)

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(
                sessionDTO = MapperProvider.sessionMapper().toSessionDTO(session),
                backendConfig = MapperProvider.serverConfigMapper().toBackendConfig(serverConfig = session.serverConfig)
            )

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, session.userId)
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(appContext, session)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder = EncryptedSettingsHolder(appContext, "${PREFERENCE_FILE_PREFIX}-${session.userId}")
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val database = Database(appContext, "main.db", userPreferencesSettings)
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
        return UserSessionScope(appContext, session, dataSourceSet, sessionRepository)
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager(
        appContext = appContext
    )

    private companion object {
        private const val SHARED_PREFERENCE_FILE_NAME = "app-preference"
        private const val PREFERENCE_FILE_PREFIX = "user-pref"
    }
}
