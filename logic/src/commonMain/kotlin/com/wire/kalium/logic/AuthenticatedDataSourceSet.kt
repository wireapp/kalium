package com.wire.kalium.logic

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.WorkScheduler
import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings

class AuthenticatedDataSourceSet(
    val authenticatedNetworkContainer: AuthenticatedNetworkContainer,
    val proteusClient: ProteusClient,
    val workScheduler: WorkScheduler,
    val syncManager: SyncManager,
    val database: Database,
    val kaliumPreferencesSettings: KaliumPreferencesSettings,
    val encryptedSettingsHolder: EncryptedSettingsHolder
)
