package com.wire.kalium.logic

import android.content.Context
import com.wire.kalium.logic.data.session.SessionDataSource
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.UserSessionScopeProviderImpl
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.GlobalWorkScheduler
import com.wire.kalium.logic.sync.GlobalWorkSchedulerImpl
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmm_settings.SettingOptions

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

    override fun getSessionRepo(): SessionRepository {
        val sessionStorage: SessionStorage = SessionStorageImpl(globalPreferences.value)
        return SessionDataSource(sessionStorage)
    }

    override val globalPreferences: Lazy<KaliumPreferences> = lazy {
        KaliumPreferencesSettings(
            EncryptedSettingsHolder(
                appContext,
                SettingOptions.AppSettings(shouldEncryptData = kaliumConfigs.shouldEncryptData)
            ).encryptedSettings
        )
    }

    override val globalDatabase: Lazy<GlobalDatabaseProvider> =
        lazy {
            GlobalDatabaseProvider(
                appContext,
                SecurityHelper(globalPreferences.value).globalDBSecret(),
                kaliumConfigs.shouldEncryptData
            )
        }

    override fun getSessionScope(userId: UserId): UserSessionScope =
        userSessionScopeProvider.value.get(userId)

    override val globalCallManager: GlobalCallManager = GlobalCallManager(
        appContext = appContext
    )

    override val globalWorkScheduler: GlobalWorkScheduler = GlobalWorkSchedulerImpl(
        appContext = appContext
    )

    override val userSessionScopeProvider: Lazy<UserSessionScopeProvider> = lazy {
            UserSessionScopeProviderImpl(
                rootPath,
                appContext,
                sessionRepository,
                getGlobalScope(),
                kaliumConfigs,
                globalPreferences.value,
                globalCallManager,
                idMapper
            )
        }
}
