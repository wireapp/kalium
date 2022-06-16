package com.wire.kalium.logic

import android.content.Context
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
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import com.wire.kalium.logic.sync.WorkSchedulerImpl
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.client.SessionStorage
import com.wire.kalium.persistence.client.SessionStorageImpl
import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import com.wire.kalium.persistence.kmm_settings.SettingOptions
import kotlinx.coroutines.runBlocking

/**
 * This class is only for platform specific variables,
 * and it should only override functions/variables from CoreLogicCommon
 */
actual class CoreLogic(
    private val appContext: Context,
    clientLabel: String,
    rootPath: String,
    private val userSessionScopeProvider: UserSessionScopeProvider = UserSessionScopeProviderImpl,
    kaliumConfigs: KaliumConfigs
) : CoreLogicCommon(clientLabel, rootPath, kaliumConfigs = kaliumConfigs) {

    override fun getSessionRepo(): SessionRepository {
        val sessionStorage: SessionStorage = SessionStorageImpl(globalPreferences.value)
        return SessionDataSource(sessionStorage)
    }

    override val globalPreferences: Lazy<KaliumPreferences> = lazy {
        KaliumPreferencesSettings(EncryptedSettingsHolder(appContext, SettingOptions.AppSettings).encryptedSettings)
    }

    override val globalDatabase: Lazy<GlobalDatabaseProvider> = lazy { GlobalDatabaseProvider(appContext, globalPreferences.value) }

    override fun getSessionScope(userId: UserId): UserSessionScope {
        return userSessionScopeProvider.get(userId) ?: run {
            val rootAccountPath = "$rootPath/${userId.domain}/${userId.value}"
            val rootProteusPath = "$rootAccountPath/proteus"
            val networkContainer = AuthenticatedNetworkContainer(
                SessionManagerImpl(sessionRepository, userId),
                ServerMetaDataManagerImpl(getGlobalScope().serverConfigRepository)
            )
            val proteusClient: ProteusClient = ProteusClientImpl(rootProteusPath)
            runBlocking { proteusClient.open() }

            val userSessionWorkScheduler: UserSessionWorkScheduler = WorkSchedulerImpl.UserSession(appContext, userId)
            val userIDEntity = idMapper.toDaoModel(userId)
            val encryptedSettingsHolder =
                EncryptedSettingsHolder(appContext, SettingOptions.UserSettings(userIDEntity))
            val userPreferencesSettings = KaliumPreferencesSettings(encryptedSettingsHolder.encryptedSettings)
            val userDatabaseProvider = UserDatabaseProvider(appContext, userIDEntity, userPreferencesSettings)
            val userDataSource = AuthenticatedDataSourceSet(
                rootAccountPath,
                networkContainer,
                proteusClient,
                userSessionWorkScheduler,
                userDatabaseProvider,
                userPreferencesSettings,
                encryptedSettingsHolder
            )
            UserSessionScope(
                appContext,
                userId,
                userDataSource,
                sessionRepository,
                globalCallManager,
                globalPreferences.value
            ).also {
                userSessionScopeProvider.add(userId, it)
            }
        }
    }

    override val globalCallManager: GlobalCallManager = GlobalCallManager(
        appContext = appContext
    )

    override val globalWorkScheduler: GlobalWorkScheduler = WorkSchedulerImpl.Global(
        appContext = appContext
    )
}
