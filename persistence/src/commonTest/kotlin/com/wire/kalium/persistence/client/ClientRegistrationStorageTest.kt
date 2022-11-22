package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun givenNoClientIdWasSaved_whenGettingTheCurrentClientId_thenResultShouldBeNull() = runTest(dispatcher) {
        assertNull(clientRegistrationStorage.getRegisteredClientId())
    }

    @Test
    fun givenAnClientIdWasSaved_whenGettingTheCurrentClientId_thenTheSavedIdShouldBeReturned() = runTest(dispatcher) {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertEquals(testId, result)
    }

    @Test
    fun givenTheLastIdWasUpdatedMultipleTimes_whenGettingTheCurrentClientId_thenTheLatestIdShouldBeReturned() = runTest(dispatcher) {
        val latestId = "sold"
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.setRegisteredClientId("give it twice")
        clientRegistrationStorage.setRegisteredClientId(latestId)

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertEquals(latestId, result)
    }

    @Test
    fun givenTheCurrentIdExisted_andWasCleared_whenGettingTheCurrentClientId_thenNullShouldBeReturned() = runTest(dispatcher) {
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.clearRegisteredClientId()

        val result = clientRegistrationStorage.getRegisteredClientId()

        assertNull(result)
    }

    @Test
    fun givenNoClientIdWasSaved_whenGettingTheRetainedClientId_thenResultShouldBeNull() = runTest(dispatcher) {
        assertNull(clientRegistrationStorage.getRetainedClientId())
    }

    @Test
    fun givenAnClientIdWasSaved_whenGettingTheRetainedClientId_thenTheSavedIdShouldBeReturned() = runTest(dispatcher) {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)

        val result = clientRegistrationStorage.getRetainedClientId()

        assertEquals(testId, result)
    }

    @Test
    fun givenTheLastIdWasUpdatedMultipleTimes_whenGettingTheRetainedClientId_thenTheLatestIdShouldBeReturned() = runTest(dispatcher) {
        val latestId = "sold"
        clientRegistrationStorage.setRegisteredClientId("give it once")
        clientRegistrationStorage.setRegisteredClientId("give it twice")
        clientRegistrationStorage.setRegisteredClientId(latestId)

        val result = clientRegistrationStorage.getRetainedClientId()

        assertEquals(latestId, result)
    }

    @Test
    fun givenTheCurrentIdExisted_andWasCleared_whenGettingTheRetainedClientId_thenTheLatestIdShouldBeReturned() = runTest(dispatcher) {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)
        clientRegistrationStorage.clearRegisteredClientId()

        val result = clientRegistrationStorage.getRetainedClientId()

        assertEquals(testId, result)
    }

    @Test
    fun givenTheRetainedIdExisted_andWasCleared_whenGettingTheRetainedClientId_thenNullShouldBeReturned() = runTest(dispatcher) {
        val testId = "😎ClientId"
        clientRegistrationStorage.setRegisteredClientId(testId)
        clientRegistrationStorage.clearRetainedClientId()

        val result = clientRegistrationStorage.getRetainedClientId()

        assertNull(result)
    }

    @Test
    fun givenHasRegisteredMLSClientWasNotSet_whenGettingHasRegisteredMLSClient_thenResultShouldBeFalse() = runTest(dispatcher) {
        assertFalse(clientRegistrationStorage.hasRegisteredMLSClient())
    }

    @Test
    fun givenHasRegisteredMLSClientWasSet_whenGettingHasRegisteredMLSClient_thenResultShouldBeTrue() = runTest(dispatcher) {
        clientRegistrationStorage.setHasRegisteredMLSClient()

        assertTrue(clientRegistrationStorage.hasRegisteredMLSClient())
    }

    @Test
    fun givenHasRegisteredMLSClientWasSet_andWasCleared_whenGettingHasRegisteredMLSClient_thenResultShouldBeFalse() = runTest(dispatcher) {
        clientRegistrationStorage.setHasRegisteredMLSClient()
        clientRegistrationStorage.clearHasRegisteredMLSClient()

        assertFalse(clientRegistrationStorage.hasRegisteredMLSClient())
    }
}
