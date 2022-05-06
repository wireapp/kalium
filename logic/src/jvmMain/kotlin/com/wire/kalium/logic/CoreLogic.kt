package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.AuthenticatedDataSourceSetProvider
import com.wire.kalium.logic.di.AuthenticatedDataSourceSetProviderImpl
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.SyncManagerImpl
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmm_settings.SettingOptions
import kotlinx.coroutines.runBlocking
import java.io.File

actual class CoreLogic(
    clientLabel: String,
    rootPath: String,
    private val authenticatedDataSourceSetProvider: AuthenticatedDataSourceSetProvider = AuthenticatedDataSourceSetProviderImpl
) : CoreLogicCommon(
    clientLabel = clientLabel, rootPath = rootPath
) {
    override fun getSessionRepo(): SessionRepository {
        val sessionStorage = SessionStorageImpl(globalPreferences)
        return SessionDataSource(sessionStorage)
    }

    override val globalPreferences: KaliumPreferences by lazy {
        KaliumPreferencesSettings(EncryptedSettingsHolder(SettingOptions.AppSettings).encryptedSettings)
    }

    override val globalDatabase: GlobalDatabaseProvider by lazy { GlobalDatabaseProvider(File("$rootPath/global-storage")) }

    override fun getSessionScope(userId: UserId): UserSessionScope {
        val dataSourceSet = authenticatedDataSourceSetProvider.get(userId) ?: run {
            val rootAccountPath = "$rootPath/${userId.domain}/${userId.value}"
            val rootProteusPath = "$rootAccountPath/proteus"
            val rootStoragePath = "$rootAccountPath/storage"
            val networkContainer = AuthenticatedNetworkContainer(SessionManagerImpl(sessionRepository, userId))

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusPath)
            runBlocking { proteusClient.open() }

            val workScheduler = WorkScheduler(this, userId)
            val syncManager = SyncManagerImpl(workScheduler)
            val encryptedSettingsHolder = EncryptedSettingsHolder(SettingOptions.UserSettings(idMapper.toDaoModel(userId)))
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val userDatabase = UserDatabaseProvider(File(rootStoragePath))

            AuthenticatedDataSourceSet(
                rootAccountPath,
                networkContainer,
                proteusClient,
                workScheduler,
                syncManager,
                userDatabase,
                userPreferencesSettings,
                encryptedSettingsHolder
            ).also {
                authenticatedDataSourceSetProvider.add(userId, it)
            }
        }

        return UserSessionScope(
            userId, dataSourceSet, sessionRepository, globalCallManager, globalPreferences
        )
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager()

}
