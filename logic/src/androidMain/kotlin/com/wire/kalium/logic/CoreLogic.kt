package com.wire.kalium.logic

import android.content.Context
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.network.UserSessionManagerImpl
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
    rootProteusDirectoryPath: String,
) : CoreLogicCommon(clientLabel, rootProteusDirectoryPath) {

    override fun getSessionRepo(): SessionRepository {
        val sessionPreferences =
            KaliumPreferencesSettings(EncryptedSettingsHolder(appContext, SHARED_PREFERENCE_FILE_NAME).encryptedSettings)
        val sessionStorage: SessionStorage = SessionStorageImpl(sessionPreferences)
        return SessionDataSource(sessionStorage, sessionMapper)
    }

    override fun getAuthenticationScope(): AuthenticationScope =
        AuthenticationScope(clientLabel, sessionRepository, appContext)

    override fun getSessionScope(session: AuthSession): UserSessionScope {
        val dataSourceSet = userScopeStorage[session] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(UserSessionManagerImpl(sessionRepository, session.userId, sessionMapper))
            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, session.userId)
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(appContext, session)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder = EncryptedSettingsHolder(appContext, "${PREFERENCE_FILE_PREFIX}-${session.userId}")
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            // FIXME: should the DB name be uniq per user ?
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

    private companion object {
        private const val SHARED_PREFERENCE_FILE_NAME = "app-preference"
        private const val PREFERENCE_FILE_PREFIX = "user-pref"
    }
}
