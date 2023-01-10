package com.wire.kalium.logic

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.GlobalWorkSchedulerImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import kotlinx.coroutines.cancel
import java.io.File

/**
 * @sample samples.logic.CoreLogicSamples.versionedAuthScope
 */
actual class CoreLogic(
    rootPath: String,
    kaliumConfigs: KaliumConfigs
) : CoreLogicCommon(
    rootPath = rootPath, kaliumConfigs = kaliumConfigs
) {

    override val globalPreferences: Lazy<GlobalPrefProvider> = lazy {
        GlobalPrefProvider(
            rootPath = rootPath,
            shouldEncryptData = kaliumConfigs.shouldEncryptData
        )
    }

    override val globalDatabase: Lazy<GlobalDatabaseProvider> = lazy {
        GlobalDatabaseProvider(File("$rootPath/global-storage"))
    }

    override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.getOrCreate(userId)

    override fun deleteSessionScope(userId: UserId) {
        userSessionScopeProvider.value.get(userId)?.cancel()
        userSessionScopeProvider.value.delete(userId)
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager()
    override val globalWorkScheduler: GlobalWorkScheduler = GlobalWorkSchedulerImpl(this)

    override val userSessionScopeProvider: Lazy<UserSessionScopeProvider> = lazy {
        UserSessionScopeProviderImpl(
            rootPathsProvider,
            getGlobalScope(),
            kaliumConfigs,
            globalPreferences.value,
            globalCallManager,
            userStorageProvider
        )
    }
}

@Suppress("MayBeConst")
actual val clientPlatform: String = "jvm"
