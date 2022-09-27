package com.wire.kalium.persistence.config

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalAppConfigStorageTest {

    private val settings: Settings = MockSettings()

    private val kaliumPreferences: KaliumPreferences = KaliumPreferencesSettings(settings)
    private lateinit var globalAppConfigStorage: GlobalAppConfigStorage

    @BeforeTest
    fun setUp() {
        globalAppConfigStorage = GlobalAppConfigStorageImpl(kaliumPreferences)
    }

    @AfterTest
    fun clear() {
        settings.clear()
    }

    @Test
    fun givenEnableLogging_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        globalAppConfigStorage.enableLogging(true)
        assertEquals(true, globalAppConfigStorage.isLoggingEnables())

        globalAppConfigStorage.enableLogging(false)
        assertEquals(false, globalAppConfigStorage.isLoggingEnables())
    }

    @Test
    fun givenPersistWebSocketStatus_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        globalAppConfigStorage.persistPersistentWebSocketConnectionStatus(true)
        assertEquals(true, globalAppConfigStorage.isPersistentWebSocketConnectionEnabledFlow().first())

        globalAppConfigStorage.persistPersistentWebSocketConnectionStatus(false)
        assertEquals(false, globalAppConfigStorage.isPersistentWebSocketConnectionEnabledFlow().first())
    }
}
