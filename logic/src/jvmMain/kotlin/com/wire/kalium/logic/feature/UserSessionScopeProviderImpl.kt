package com.wire.kalium.logic.feature

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusClientImpl
import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.ServerMetaDataManagerImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.UserSessionWorkSchedulerImpl
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmmSettings.SettingOptions
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.runBlocking
import java.io.File

@Suppress("LongParameterList")
actual class UserSessionScopeProviderImpl(
    private val rootPath: String,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs,
    private val globalPreferences: GlobalPrefProvider,
    private val globalCallManager: GlobalCallManager,
    private val idMapper: IdMapper
) : UserSessionScopeProviderCommon() {

    override fun create(userId: UserId): UserSessionScope {
        val rootAccountPath = "$rootPath/${userId.domain}/${userId.value}"
        val rootProteusPath = "$rootAccountPath/proteus"
        val rootStoragePath = "$rootAccountPath/storage"
        val rootFileSystemPath = AssetsStorageFolder("$rootStoragePath/files")
        val rootCachePath = CacheFolder("$rootAccountPath/cache")
        val dataStoragePaths = DataStoragePaths(rootFileSystemPath, rootCachePath)
        val networkContainer = AuthenticatedNetworkContainer(
            SessionManagerImpl(globalScope.sessionRepository, userId, tokenStorage = globalPreferences.authTokenStorage),
            ServerMetaDataManagerImpl(globalScope.serverConfigRepository),
            developmentApiEnabled = kaliumConfigs.developmentApiEnabled
        )

        val proteusClient: ProteusClient = ProteusClientImpl(rootProteusPath)
        runBlocking { proteusClient.open() }

        val userSessionWorkScheduler = UserSessionWorkSchedulerImpl(userId)
        val encryptedSettingsHolder = EncryptedSettingsHolder(
            rootPath,
            SettingOptions.UserSettings(
                shouldEncryptData = kaliumConfigs.shouldEncryptData,
                idMapper.toDaoModel(userId)
            )
        )
        val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
        val userDatabase = UserDatabaseProvider(File(rootStoragePath), KaliumDispatcherImpl.io)

        val userDataSource = AuthenticatedDataSourceSet(
            rootAccountPath,
            networkContainer,
            proteusClient,
            userSessionWorkScheduler,
            userDatabase,
            userPreferencesSettings,
            encryptedSettingsHolder
        )
        return UserSessionScope(
            userId,
            userDataSource,
            globalScope,
            globalCallManager,
            globalPreferences,
            dataStoragePaths,
            kaliumConfigs,
            this
        )
    }
}
