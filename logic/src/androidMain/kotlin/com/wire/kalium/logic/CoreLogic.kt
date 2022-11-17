package com.wire.kalium.logic

import android.content.Context
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.GlobalWorkSchedulerImpl
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmmSettings.GlobalPrefProvider
import kotlinx.coroutines.cancel

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from CoreLogicCommon
 */
actual class CoreLogic(
    private val appContext: Context,
    clientLabel: String,
    rootPath: String,
    kaliumConfigs: KaliumConfigs
) : CoreLogicCommon(clientLabel, rootPath, kaliumConfigs = kaliumConfigs) {

    override val globalPreferences: Lazy<GlobalPrefProvider> = lazy {
        GlobalPrefProvider(appContext, kaliumConfigs.shouldEncryptData)
    }

    override val globalDatabase: Lazy<GlobalDatabaseProvider> =
        lazy {
            GlobalDatabaseProvider(
                appContext,
                SecurityHelper(globalPreferences.value.passphraseStorage).globalDBSecret(),
                kaliumConfigs.shouldEncryptData
            )
        }

    override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.getOrCreate(userId)

    override fun deleteSessionScope(userId: UserId) {
        userSessionScopeProvider.value.get(userId)?.cancel()
        userSessionScopeProvider.value.delete(userId)
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager(
        appContext = appContext
    )

    override val globalWorkScheduler: GlobalWorkScheduler = GlobalWorkSchedulerImpl(
        appContext = appContext
    )

    override val userSessionScopeProvider: Lazy<UserSessionScopeProvider> = lazy {
        UserSessionScopeProviderImpl(
            rootPathsProvider,
            appContext,
            getGlobalScope(),
            kaliumConfigs,
            globalPreferences.value,
            globalCallManager,
            userStorageProvider
        )
    }
}
