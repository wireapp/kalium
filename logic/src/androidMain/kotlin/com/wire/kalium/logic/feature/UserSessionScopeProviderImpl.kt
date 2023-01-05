package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DBFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.RootPathsProvider
import com.wire.kalium.logic.di.UserStorageProvider
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.FeatureSupportImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.UserSessionWorkSchedulerImpl
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

@Suppress("LongParameterList")
internal actual class UserSessionScopeProviderImpl(
    private val rootPathsProvider: RootPathsProvider,
    private val appContext: Context,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs,
    private val globalPreferences: GlobalPrefProvider,
    private val globalCallManager: GlobalCallManager,
    private val userStorageProvider: UserStorageProvider
) : UserSessionScopeProviderCommon(globalCallManager, userStorageProvider) {

    override fun create(userId: UserId): UserSessionScope {
        val userIdEntity = userId.toDao()
        val rootAccountPath = rootPathsProvider.rootAccountPath(userId)
        val rootProteusPath = rootPathsProvider.rootProteusPath(userId)
        val rootFileSystemPath = AssetsStorageFolder("${appContext.filesDir}/${userId.domain}/${userId.value}")
        val dbPath = DBFolder(
            "${appContext.getDatabasePath(FileNameUtil.userDBName(userIdEntity))}"
        )
        val rootCachePath = CacheFolder("${appContext.cacheDir}/${userId.domain}/${userId.value}")
        val dataStoragePaths = DataStoragePaths(rootFileSystemPath, rootCachePath, dbPath)
        val sessionManager = SessionManagerImpl(
            globalScope.sessionRepository,
            userId,
            globalPreferences.authTokenStorage
        )
        val networkContainer: AuthenticatedNetworkContainer =
            AuthenticatedNetworkContainer.create(sessionManager, UserIdDTO(userId.value, userId.domain))
        val featureSupport = FeatureSupportImpl(kaliumConfigs, sessionManager.serverConfig().metaData.commonApiVersion.version)
        val proteusClientProvider = ProteusClientProviderImpl(rootProteusPath, userId, globalPreferences.passphraseStorage, kaliumConfigs)

        val userSessionWorkScheduler = UserSessionWorkSchedulerImpl(appContext, userId)

        val userDataSource = AuthenticatedDataSourceSet(
            rootAccountPath,
            networkContainer,
            proteusClientProvider,
            userSessionWorkScheduler
        )
        return UserSessionScope(
            appContext,
            userId,
            userDataSource,
            globalScope,
            globalCallManager,
            globalPreferences,
            sessionManager,
            dataStoragePaths,
            kaliumConfigs,
            featureSupport,
            userStorageProvider,
            this
        )
    }
}
