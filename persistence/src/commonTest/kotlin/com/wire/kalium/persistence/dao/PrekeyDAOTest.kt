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

import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PrekeyDAOTest : BaseDatabaseTest() {

    private lateinit var prekeyDAO: PrekeyDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        prekeyDAO = db.prekeyDAO
    }

    @Test
    fun givenOTRLastPrekeyId_whenUpdating_thenItsOnlyUpdatedIfTheNewIdIsHigher() = runTest {
        val currentStoredId = 100
        prekeyDAO.forceInsertMostRecentPreKeyId(100)

        prekeyDAO.updateMostRecentPreKeyId(50)
        assertEquals(currentStoredId, prekeyDAO.mostRecentPreKeyId())

        prekeyDAO.updateMostRecentPreKeyId(101)
        assertEquals(101, prekeyDAO.mostRecentPreKeyId())
    }

    @Test
    fun whenForceInsertingPrekeyId_thenTheNewIdIsInserted() = runTest {
        prekeyDAO.forceInsertMostRecentPreKeyId(100)

        prekeyDAO.forceInsertMostRecentPreKeyId(50)
        assertEquals(50, prekeyDAO.mostRecentPreKeyId())
    }

    @Test
    fun whenNotLastPreKeyIdIsStored_thenReturnNull() = runTest {
        assertEquals(null, prekeyDAO.mostRecentPreKeyId())
    }
}
