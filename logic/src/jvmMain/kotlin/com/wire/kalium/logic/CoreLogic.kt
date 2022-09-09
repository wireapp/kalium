package com.wire.kalium.logic

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
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmm_settings.SettingOptions
import kotlinx.coroutines.cancel
import java.io.File

actual class CoreLogic(
    clientLabel: String,
    rootPath: String,
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
        KaliumPreferencesSettings(
            EncryptedSettingsHolder(
                rootPath,
                SettingOptions.AppSettings(
                    shouldEncryptData = kaliumConfigs.shouldEncryptData
                )
            ).encryptedSettings
        )
    }

    override val globalDatabase: Lazy<GlobalDatabaseProvider> = lazy { GlobalDatabaseProvider(File("$rootPath/global-storage")) }

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
                rootPath,
                sessionRepository,
                getGlobalScope(),
                kaliumConfigs,
                globalPreferences.value,
                globalCallManager,
                idMapper
            )
        }
}
