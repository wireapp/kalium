package com.wire.kalium.persistence.client

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserConfigStorageTest {
    private val settings: Settings = MockSettings()

    private val kaliumPreferences: KaliumPreferences = KaliumPreferencesSettings(settings)
    private lateinit var userConfigStorage: UserConfigStorage

    @BeforeTest
    fun setUp() {
        userConfigStorage = UserConfigStorageImpl(kaliumPreferences)
    }

    @AfterTest
    fun clear() {
        settings.clear()
    }

    @Test
    fun givenEnableLogging_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        userConfigStorage.enableLogging(true)
        assertEquals(true, userConfigStorage.isLoggingEnables())

        userConfigStorage.enableLogging(false)
        assertEquals(false, userConfigStorage.isLoggingEnables())
    }

    @Test
    fun givenPersistWebSocketStatus_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        userConfigStorage.persistPersistentWebSocketConnectionStatus(true)
        assertEquals(true, userConfigStorage.isPersistentWebSocketConnectionEnabledFlow().first())

        userConfigStorage.persistPersistentWebSocketConnectionStatus(false)
        assertEquals(false, userConfigStorage.isPersistentWebSocketConnectionEnabledFlow().first())
    }

    @Test
    fun givenAFileSharingStatusValue_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        userConfigStorage.persistFileSharingStatus(true, null)
        assertEquals(IsFileSharingEnabledEntity(true, null), userConfigStorage.isFileSharingEnabled())

        userConfigStorage.persistFileSharingStatus(false, null)
        assertEquals(IsFileSharingEnabledEntity(false, null), userConfigStorage.isFileSharingEnabled())
    }

    @Test
    fun givenAClassifiedDomainsStatusValue_whenCAllPersistItSaveAndThenCanRestoreTheValueLocally() = runTest {
        userConfigStorage.persistClassifiedDomainsStatus(true, listOf("bella.com", "anta.wire"))
        assertEquals(
            ClassifiedDomainsEntity(true, listOf("bella.com", "anta.wire")),
            userConfigStorage.isClassifiedDomainsEnabledFlow().first()
        )
    }
}
