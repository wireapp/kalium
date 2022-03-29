package com.wire.kalium.logic

import android.content.Context
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmm_settings.SettingOptions
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
            KaliumPreferencesSettings(EncryptedSettingsHolder(appContext, SettingOptions.AppSettings).encryptedSettings)
        val sessionStorage: SessionStorage = SessionStorageImpl(sessionPreferences)
        return SessionDataSource(sessionStorage)
    }

    override fun getAuthenticationScope(): AuthenticationScope =
        AuthenticationScope(clientLabel, sessionRepository, appContext)

    override fun getSessionScope(userId: UserId): UserSessionScope {
        val dataSourceSet = userScopeStorage[userId] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(SessionManagerImpl(sessionRepository, userId))
            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, userId.value)
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(appContext, userId)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder =
                EncryptedSettingsHolder(appContext, SettingOptions.UserSettings(idMapper.toDaoModel(userId)))
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val database = Database(appContext, userId.value, userPreferencesSettings)
            AuthenticatedDataSourceSet(
                networkContainer,
                proteusClient,
                workScheduler,
                syncManager,
                database,
                userPreferencesSettings,
                encryptedSettingsHolder
            ).also {
                userScopeStorage[userId] = it
            }
        }
        return UserSessionScope(appContext, userId, dataSourceSet, sessionRepository)
    }
}
