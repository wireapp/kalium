package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings

@Suppress("LongParameterList") // Suppressed as it's an old issue
class AuthenticatedDataSourceSet(
    val authenticatedRootDir: String,
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    val proteusClient: ProteusClient,
    val workScheduler: WorkScheduler,
    val userSessionWorkScheduler: UserSessionWorkScheduler,
    val syncManager: SyncManager,
    val userDatabaseProvider: UserDatabaseProvider,
    val kaliumPreferencesSettings: KaliumPreferencesSettings,
    val encryptedSettingsHolder: EncryptedSettingsHolder
)
