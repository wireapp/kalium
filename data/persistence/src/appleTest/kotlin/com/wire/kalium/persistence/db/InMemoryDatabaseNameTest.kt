/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.QueryResult
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class InMemoryDatabaseNameTest {

    @Test
    fun givenTwoNamesForOneUser_whenWritingToOne_thenTheOtherStaysEmptyAndBothKeepTheUserAsSelf() = runTest {
        val user = UserIDEntity("same-user", "example.com")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = inMemoryDatabase(user, dispatcher, databaseName = "in-memory-name-test-first")
        val second = inMemoryDatabase(user, dispatcher, databaseName = "in-memory-name-test-second")
        try {
            first.metadataDAO.insertValue("written", "key")

            assertEquals("written", first.metadataDAO.valueByKey("key"))
            assertNull(second.metadataDAO.valueByKey("key"))
            assertEquals(user.toString(), first.selfUserId())
            assertEquals(user.toString(), second.selfUserId())
        } finally {
            clearInMemoryDatabase(databaseName = "in-memory-name-test-first")
            clearInMemoryDatabase(databaseName = "in-memory-name-test-second")
        }
    }

    @Test
    fun givenNamedDatabase_whenNuked_thenItReturnsTrueAndItsCacheEntryIsRemoved() = runTest {
        val user = UserIDEntity("nuke-user", "example.com")
        val name = "in-memory-name-test-nuke"
        val database = inMemoryDatabase(user, StandardTestDispatcher(testScheduler), databaseName = name)
        try {
            val nuked = database.nuke()

            assertTrue(nuked)
            assertFalse(clearInMemoryDatabase(databaseName = name))
        } finally {
            clearInMemoryDatabase(databaseName = name)
        }
    }

    @Test
    fun givenNamedAndDefaultDatabasesOfOneUser_whenNamedIsNuked_thenDefaultKeepsItsData() = runTest {
        val user = UserIDEntity("nuke-other-user", "example.com")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val default = inMemoryDatabase(user, dispatcher)
        val named = inMemoryDatabase(user, dispatcher, databaseName = "in-memory-name-test-nuke-named")
        try {
            default.metadataDAO.insertValue("kept", "key")

            named.nuke()

            assertEquals("kept", default.metadataDAO.valueByKey("key"))
            assertTrue(clearInMemoryDatabase(user))
        } finally {
            clearInMemoryDatabase(user)
            clearInMemoryDatabase(databaseName = "in-memory-name-test-nuke-named")
        }
    }

    private suspend fun UserDatabaseBuilder.selfUserId(): String? = sqlDriver.executeQuery(
        identifier = null,
        sql = "SELECT id FROM SelfUser",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
        parameters = 0
    ).await()
}
