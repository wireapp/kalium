package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmm_settings.SettingOptions
import kotlinx.coroutines.runBlocking

actual class CoreLogic(clientLabel: String, rootProteusDirectoryPath: String) : CoreLogicCommon(
    clientLabel = clientLabel, rootProteusDirectoryPath = rootProteusDirectoryPath
) {
    override fun getSessionRepo(): SessionRepository {
        val kaliumPreferences = KaliumPreferencesSettings(EncryptedSettingsHolder(SettingOptions.AppSettings).encryptedSettings)
        val sessionStorage = SessionStorageImpl(kaliumPreferences)
        return SessionDataSource(sessionStorage)
    }

    override fun getSessionScope(userId: UserId): UserSessionScope {
        val dataSourceSet = userScopeStorage[userId] ?: run {
            val networkContainer = AuthenticatedNetworkContainer(SessionManagerImpl(sessionRepository, userId))

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusDirectoryPath, idMapper.toCryptoQualifiedIDId(userId))
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(this, userId)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder = EncryptedSettingsHolder(SettingOptions.UserSettings(idMapper.toDaoModel(userId)))
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val userDatabaseProvider = UserDatabaseProvider()

            AuthenticatedDataSourceSet(
                rootProteusDirectoryPath,
                networkContainer,
                proteusClient,
                workScheduler,
                syncManager,
                userDatabaseProvider,
                userPreferencesSettings,
                encryptedSettingsHolder
            ).also {
                userScopeStorage[userId] = it
            }
        }

        return UserSessionScope(
            userId,
            dataSourceSet,
            sessionRepository,
            globalCallManager
        )
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager()

}
