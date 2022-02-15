package com.wire.kalium.persistence.client

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientRegistrationStorageTest {

    private val mockSettings: Settings = MockSettings()
    private val kaliumPreferences = KaliumPreferencesSettings(mockSettings)

    private lateinit var clientRegistrationStorage: ClientRegistrationStorageImpl

    @BeforeTest
    fun setup(){
        mockSettings.clear()
        clientRegistrationStorage = ClientRegistrationStorageImpl(kaliumPreferences)
    }

    @Test
    fun givenNoClientIdWasSaved_whenGettingTheLastClientId_thenResultShouldBeNull(){
        assertNull(clientRegistrationStorage.registeredClientId)
    }

    @Test
    fun givenAnClientIdWasSaved_whenGettingTheLastClientId_thenTheSavedIdShouldBeReturned(){
        val testId = "😎ClientId"
        clientRegistrationStorage.registeredClientId = testId

        val result = clientRegistrationStorage.registeredClientId

        assertEquals(testId, result)
    }

    @Test
    fun givenTheLastIdWasUpdatedMultipleTimes_whenGettingTheLastClientId_thenTheLatestIdShouldBeReturned(){
        val latestId = "sold"
        clientRegistrationStorage.registeredClientId = "give it once"
        clientRegistrationStorage.registeredClientId = "give it twice"
        clientRegistrationStorage.registeredClientId = latestId

        val result = clientRegistrationStorage.registeredClientId

        assertEquals(latestId, result)
    }

    @Test
    fun givenTheLastIdExisted_andWasUpdatedToNull_whenGettingTheLastClientId_thenNullShouldBeReturned(){
        clientRegistrationStorage.registeredClientId = "give it once"
        clientRegistrationStorage.registeredClientId = null

        val result = clientRegistrationStorage.registeredClientId

        assertNull(result)
    }
}
