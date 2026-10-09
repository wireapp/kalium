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
import com.wire.kalium.persistence.config.UserConfigStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MessageContentPreferencePersistenceTest : BaseDatabaseTest() {
    @Test
    fun absentAndSavedValuesSurviveReopeningAndStayAccountLocal() = runTest(dispatcher) {
        val first = UserIDEntity("first", "example.test")
        val second = UserIDEntity("second", "example.test")
        val database = createDatabase(first, encryptedDBSecret, true)
        val other = createDatabase(second, encryptedDBSecret, true)
        try {
            assertNull(database.userPrefsDAO.readMessageContentPreference())
            database.userPrefsDAO.persistMessageContentPreference(false)
            assertEquals(false, database.userPrefsDAO.readMessageContentPreference())
            assertNull(other.userPrefsDAO.readMessageContentPreference())
            other.userPrefsDAO.persistMessageContentPreference(true)
            assertEquals(false, database.userPrefsDAO.readMessageContentPreference())
        } finally {
            database.close()
            other.close()
        }
        val reopened = createDatabase(first, encryptedDBSecret, true)
        try {
            assertEquals(false, reopened.userPrefsDAO.readMessageContentPreference())
            reopened.userPrefsDAO.persistMessageContentPreference(true)
            assertEquals(true, reopened.userPrefsDAO.readMessageContentPreference())
        } finally {
            reopened.close()
            deleteDatabase(first)
            deleteDatabase(second)
        }
    }

    @Test
    fun malformedValuesAreNotAbsenceAndExplicitSaveRecovers() = runTest(dispatcher) {
        val user = UserIDEntity("corrupt", "example.test")
        val database = createDatabase(user, encryptedDBSecret, true)
        try {
            val key = UserConfigStorage.UserPreferences.MESSAGE_CONTENT_ENABLED.key
            for (value in listOf("", "TRUE", "1", "false ", "garbage")) {
                database.metadataDAO.insertValue(value, key)
                assertFailsWith<IllegalArgumentException> { database.userPrefsDAO.readMessageContentPreference() }
                assertEquals(value, database.metadataDAO.valueByKey(key))
            }
            database.userPrefsDAO.persistMessageContentPreference(false)
            assertEquals(false, database.userPrefsDAO.readMessageContentPreference())
        } finally {
            database.close()
            deleteDatabase(user)
        }
    }
}
