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

package com.wire.kalium.persistence.dao

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataDAOTest : BaseDatabaseTest() {

    private val value1 = "value1"
    private val value2 = "value2"

    private val key1 = "key1"
    private val key2 = "key2"
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    private lateinit var metadataDAO: MetadataDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        metadataDAO = db.metadataDAO
    }

    @Test
    fun givenNonExistingKey_thenValueCanBeStored() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        assertEquals(value1, metadataDAO.valueByKeyFlow(key1).first())
    }

    @Test
    fun givenExistingKey_thenExistingValueCanBeOverwritten() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        metadataDAO.insertValue(value2, key1)
        assertEquals(value2, metadataDAO.valueByKeyFlow(key1).first())
    }

    @Test
    fun givenExistingKey_thenValueCanBeRetrieved() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        assertEquals(value1, metadataDAO.valueByKeyFlow(key1).first())
    }

    @Test
    fun giveNonExistingKey_thenNullValueWillBeReturned() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        assertNull(metadataDAO.valueByKeyFlow(key2).first())
    }

    @Test
    fun giveExistingKey_whenValueHasBeenModified_thenEmitNewValue() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        metadataDAO.valueByKeyFlow(key1).test {
            assertEquals(value1, this.awaitItem())
            metadataDAO.insertValue(value2, key1)
            assertEquals(value2, this.awaitItem())
        }
    }

    @Test
    fun giveExistingKey_whenOtherValueHasBeenModified_thenDoNotReEmitTheSameValue() = runTest(dispatcher) {
        metadataDAO.insertValue(value = value1, key = key1)
        metadataDAO.valueByKeyFlow(key1).test {
            assertEquals(value1, awaitItem())
            metadataDAO.insertValue(value = value2, key = key2)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
