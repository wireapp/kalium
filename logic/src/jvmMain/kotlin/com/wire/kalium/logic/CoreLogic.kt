package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.util.FileNameUtil
import kotlinx.coroutines.runBlocking

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String) :
    CoreLogicCommon(
        clientLabel = clientLabel,
        rootProteusDirectoryPath = rootProteusDirectoryPath
    ) {
    override fun getSessionRepo(): SessionRepository {
        val kaliumPreferences = KaliumPreferencesSettings(EncryptedSettingsHolder(".pref").encryptedSettings)
        val sessionStorage = SessionStorageImpl(kaliumPreferences)
        return SessionDataSource(sessionStorage)
    }

    override fun getAuthenticationScope(): AuthenticationScope {
        return AuthenticationScope(".", clientLabel, sessionRepository)
    }

    override fun getSessionScope(userId: NonQualifiedUserId): UserSessionScope {
        val dataSourceSet = userScopeStorage[userId] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(SessionManagerImpl(sessionRepository, userId))

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, userId)
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(this, userId)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder = EncryptedSettingsHolder(FileNameUtil.userPrefFile(userId))
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val database = Database()

            AuthenticatedDataSourceSet(
                rootProteusDirectoryPath,
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

        return UserSessionScope(userId, dataSourceSet, sessionRepository)
    }
}
