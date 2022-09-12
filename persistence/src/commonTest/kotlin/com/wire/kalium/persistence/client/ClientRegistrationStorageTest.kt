package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ClientRegistrationStorageTest : BaseDatabaseTest() {

    private lateinit var clientRegistrationStorage: ClientRegistrationStorageImpl

    @BeforeTest
    fun setup() {
        deleteDatabase()
        val database = createDatabase()
        clientRegistrationStorage = ClientRegistrationStorageImpl(database.metadataDAO)
    }

    @Test
    fun givenNoClientIdWasSaved_whenGettingTheCurrentClientId_thenResultShouldBeNull() = runTest {
        assertNull(clientRegistrationStorage.getRegisteredClientId())
    }

    @Test
    fun givenAnClientIdWasSaved_whenGettingTheCurrentClientId_thenTheSavedIdShouldBeReturned() = runTest {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertEquals(testId, result)
    }

    @Test
    fun givenTheLastIdWasUpdatedMultipleTimes_whenGettingTheCurrentClientId_thenTheLatestIdShouldBeReturned() = runTest {
        val latestId = "sold"
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.setRegisteredClientId("give it twice")
        clientRegistrationStorage.setRegisteredClientId(latestId)

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertEquals(latestId, result)
    }

    @Test
    fun givenTheCurrentIdExisted_andWasCleared_whenGettingTheCurrentClientId_thenNullShouldBeReturned() = runTest {
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.clearRegisteredClientId()

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertNull(result)
    }

    @Test
    fun givenNoClientIdWasSaved_whenGettingTheRetainedClientId_thenResultShouldBeNull() = runTest {
        assertNull(clientRegistrationStorage.getRetainedClientId())
    }

    @Test
    fun givenAnClientIdWasSaved_whenGettingTheRetainedClientId_thenTheSavedIdShouldBeReturned() = runTest {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)

        val result = clientRegistrationStorage.getRetainedClientId()

        assertEquals(testId, result)
    }

    @Test
    fun givenTheLastIdWasUpdatedMultipleTimes_whenGettingTheRetainedClientId_thenTheLatestIdShouldBeReturned() = runTest {
        val latestId = "sold"
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.setRegisteredClientId("give it twice")
        clientRegistrationStorage.setRegisteredClientId(latestId)

        val result = clientRegistrationStorage.getRetainedClientId()

        assertEquals(latestId, result)
    }

    @Test
    fun givenTheCurrentIdExisted_andWasCleared_whenGettingTheRetainedClientId_thenTheLatestIdShouldBeReturned() = runTest {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)
        clientRegistrationStorage.clearRegisteredClientId()

        val result = clientRegistrationStorage.getRetainedClientId()

        assertEquals(testId, result)
    }

    @Test
    fun givenTheRetainedIdExisted_andWasCleared_whenGettingTheRetainedClientId_thenNullShouldBeReturned() = runTest {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)
        clientRegistrationStorage.clearRetainedClientId()

        val result = clientRegistrationStorage.getRetainedClientId()

        assertNull(result)
    }
}
