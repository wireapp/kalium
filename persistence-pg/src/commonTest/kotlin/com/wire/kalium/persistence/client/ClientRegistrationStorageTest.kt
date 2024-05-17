/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
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

    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setup() {
        deleteDatabase(selfUserId)
        val database = createDatabase(selfUserId, encryptedDBSecret, true)
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
