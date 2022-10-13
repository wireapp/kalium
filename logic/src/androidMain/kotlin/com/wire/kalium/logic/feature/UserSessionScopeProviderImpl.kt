package com.wire.kalium.logic.feature

import android.content.Context
import com.wire.kalium.logic.GlobalKaliumScope
import com.wire.kalium.logic.data.asset.AssetsStorageFolder
import com.wire.kalium.logic.data.asset.CacheFolder
import com.wire.kalium.logic.data.asset.DataStoragePaths
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.FeatureSupportImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.network.SessionManagerImpl
import com.wire.kalium.logic.sync.UserSessionWorkSchedulerImpl
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import com.wire.kalium.persistence.kmmSettings.UserPrefBuilder
import com.wire.kalium.util.KaliumDispatcherImpl

@Suppress("LongParameterList")
actual class UserSessionScopeProviderImpl(
    private val rootPath: String,
    private val appContext: Context,
    private val globalScope: GlobalKaliumScope,
    private val kaliumConfigs: KaliumConfigs,
    private val globalPreferences: GlobalPrefProvider,
    private val globalCallManager: GlobalCallManager,
    private val idMapper: IdMapper
) : UserSessionScopeProviderCommon(globalCallManager) {

    override fun create(userId: UserId): UserSessionScope {
        val rootAccountPath = "$rootPath/${userId.domain}/${userId.value}"
        val rootProteusPath = "$rootAccountPath/proteus"
        val rootFileSystemPath = AssetsStorageFolder("${appContext.filesDir}/${userId.domain}/${userId.value}")
        val rootCachePath = CacheFolder("${appContext.cacheDir}/${userId.domain}/${userId.value}")
        val dataStoragePaths = DataStoragePaths(rootFileSystemPath, rootCachePath)
        val sessionManager = SessionManagerImpl(globalScope.sessionRepository, userId, globalPreferences.authTokenStorage)
        val networkContainer: AuthenticatedNetworkContainer = AuthenticatedNetworkContainer.create(sessionManager)
        val featureSupport = FeatureSupportImpl(kaliumConfigs, sessionManager.session().second.metaData.commonApiVersion.version)
        val proteusClientProvider = ProteusClientProviderImpl(rootProteusPath)

        val userSessionWorkScheduler = UserSessionWorkSchedulerImpl(appContext, userId)
        val userIDEntity = idMapper.toDaoModel(userId)
        val userPrefBuilder = UserPrefBuilder(
            userIDEntity,
            appContext,
            kaliumConfigs.shouldEncryptData
        )
        val userDatabaseProvider =
            UserDatabaseProvider(
                appContext,
                userIDEntity,
                SecurityHelper(globalPreferences.passphraseStorage).userDBSecret(userId),
                kaliumConfigs.shouldEncryptData,
                KaliumDispatcherImpl.io
            )
        val userDataSource = AuthenticatedDataSourceSet(
            rootAccountPath,
            networkContainer,
            proteusClientProvider,
            userSessionWorkScheduler,
            userDatabaseProvider,
            userPrefBuilder
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
            featureSupport,
            this
        )
    }

}
