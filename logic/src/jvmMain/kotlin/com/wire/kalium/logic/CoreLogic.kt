package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.UserSessionScopeProvider
import com.wire.kalium.logic.di.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.auth.ServerMetaDataManagerImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.GlobalWorkSchedulerImpl
import com.wire.kalium.logic.sync.UserSessionWorkSchedulerImpl
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
    private val userSessionScopeProvider: UserSessionScopeProvider = UserSessionScopeProviderImpl,
    kaliumConfigs: KaliumConfigs
) : CoreLogicCommon(
    clientLabel = clientLabel, rootPath = rootPath, kaliumConfigs = kaliumConfigs
) {
    override fun getSessionRepo(): SessionRepository {
        // TODO: make lazier
        val sessionStorage = SessionStorageImpl(globalPreferences.value)
        return SessionDataSource(sessionStorage)
    }

    override val globalPreferences: Lazy<KaliumPreferences> = lazy {
        KaliumPreferencesSettings(EncryptedSettingsHolder(SettingOptions.AppSettings).encryptedSettings)
    }

    override val globalDatabase: Lazy<GlobalDatabaseProvider> = lazy { GlobalDatabaseProvider(File("$rootPath/global-storage")) }

    override fun getSessionScope(userId: UserId): UserSessionScope {
        return userSessionScopeProvider.get(userId) ?: run {
            val rootAccountPath = "$rootPath/${userId.domain}/${userId.value}"
            val rootProteusPath = "$rootAccountPath/proteus"
            val rootStoragePath = "$rootAccountPath/storage"
            val networkContainer = AuthenticatedNetworkContainer(
                SessionManagerImpl(sessionRepository, userId),
                ServerMetaDataManagerImpl(getGlobalScope().serverConfigRepository)
            )

            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusPath)
            runBlocking { proteusClient.open() }

            val userSessionWorkScheduler = UserSessionWorkSchedulerImpl(this, userId)
            val encryptedSettingsHolder = EncryptedSettingsHolder(SettingOptions.UserSettings(idMapper.toDaoModel(userId)))
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val userDatabase = UserDatabaseProvider(File(rootStoragePath))

            val userDataSource = AuthenticatedDataSourceSet(
                rootAccountPath,
                networkContainer,
                proteusClient,
                userSessionWorkScheduler,
                userDatabase,
                userPreferencesSettings,
                encryptedSettingsHolder
            )
            UserSessionScope(
                userId,
                userDataSource,
                sessionRepository,
                globalCallManager,
                // TODO: make lazier
                globalPreferences.value,
                kaliumConfigs
            ).also {
                userSessionScopeProvider.add(userId, it)
            }
        }
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager()
    override val globalWorkScheduler: GlobalWorkScheduler = GlobalWorkSchedulerImpl(this)

}
