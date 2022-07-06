package com.wire.kalium.persistence.client

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientRegistrationStorageTest: BaseDatabaseTest() {

    private lateinit var clientRegistrationStorage: ClientRegistrationStorageImpl

    @BeforeTest
    fun setup(){
        deleteDatabase()
        val database = createDatabase()
        clientRegistrationStorage = ClientRegistrationStorageImpl(database.metadataDAO)
    }

    @Test
    fun givenNoClientIdWasSaved_whenGettingTheLastClientId_thenResultShouldBeNull() = runTest {
        assertNull(clientRegistrationStorage.getRegisteredClientId())
    }

    @Test
    fun givenAnClientIdWasSaved_whenGettingTheLastClientId_thenTheSavedIdShouldBeReturned() = runTest {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertEquals(testId, result)
    }

    @Test
    fun givenTheLastIdWasUpdatedMultipleTimes_whenGettingTheLastClientId_thenTheLatestIdShouldBeReturned() = runTest {
        val latestId = "sold"
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.setRegisteredClientId("give it twice")
        clientRegistrationStorage.setRegisteredClientId(latestId)

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertEquals(latestId, result)
    }

    @Test
    fun givenTheLastIdExisted_andWasUpdatedToNull_whenGettingTheLastClientId_thenNullShouldBeReturned() = runTest {
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.setRegisteredClientId(null)

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertNull(result)
    }
}
