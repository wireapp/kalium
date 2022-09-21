package com.wire.kalium.persistence.config

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.kmmSettings.KaliumPreferences
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
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
