package com.wire.kalium.logic.feature

import android.content.Context
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
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmm_settings.SettingOptions
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.runBlocking

@Suppress("LongParameterList")
actual class UserSessionScopeProviderImpl(
    private val rootPath: String,
    private val appContext: Context,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs,
    private val globalPreferences: KaliumPreferences,
    private val globalCallManager: GlobalCallManager,
    private val idMapper: IdMapper,
    private val globalDatabase: GlobalDatabaseProvider
) : UserSessionScopeProviderCommon() {

    override fun create(userId: UserId): UserSessionScope {
        val rootAccountPath = "$rootPath/${userId.domain}/${userId.value}"
        val rootProteusPath = "$rootAccountPath/proteus"
        val rootFileSystemPath = AssetsStorageFolder("${appContext.filesDir}/${userId.domain}/${userId.value}")
        val rootCachePath = CacheFolder("${appContext.cacheDir}/${userId.domain}/${userId.value}")
        val dataStoragePaths = DataStoragePaths(rootFileSystemPath, rootCachePath)
        val networkContainer = AuthenticatedNetworkContainer(
            SessionManagerImpl(globalScope.sessionRepository, userId, AuthTokenStorage(globalPreferences)),
            ServerMetaDataManagerImpl(globalScope.serverConfigRepository),
            developmentApiEnabled = kaliumConfigs.developmentApiEnabled
        )
        val proteusClient: ProteusClient = ProteusClientImpl(rootProteusPath)
        runBlocking { proteusClient.open() }

        val userSessionWorkScheduler = UserSessionWorkSchedulerImpl(appContext, userId)
        val userIDEntity = idMapper.toDaoModel(userId)
        val encryptedSettingsHolder =
            EncryptedSettingsHolder(
                appContext,
                SettingOptions.UserSettings(shouldEncryptData = kaliumConfigs.shouldEncryptData, userIDEntity)
            )
        val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
        val userDatabaseProvider =
            UserDatabaseProvider(
                appContext,
                userIDEntity,
                SecurityHelper(globalPreferences).userDBSecret(userId),
                kaliumConfigs.shouldEncryptData,
                KaliumDispatcherImpl.io
            )
        val userDataSource = AuthenticatedDataSourceSet(
            rootAccountPath,
            networkContainer,
            proteusClient,
            userSessionWorkScheduler,
            userDatabaseProvider,
            userPreferencesSettings,
            encryptedSettingsHolder
        )
        return UserSessionScope(
            appContext,
            userId,
            userDataSource,
            globalScope,
            globalCallManager,
            globalPreferences,
            dataStoragePaths,
            kaliumConfigs,
            this,
            globalDatabase
        )
    }

}
