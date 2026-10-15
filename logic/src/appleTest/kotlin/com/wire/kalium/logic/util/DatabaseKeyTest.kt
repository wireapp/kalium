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

package com.wire.kalium.logic.util

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatabaseKeyTest {

    private val databasePath = NSTemporaryDirectory() + "kalium-database-key-test-" + NSUUID().UUIDString

    @AfterTest
    fun tearDown() {
        NSFileManager.defaultManager.removeItemAtPath(databasePath, null)
    }

    @Test
    fun givenNoDatabase_whenGettingItsKey_thenTheStoredOrANewKeyIsUsed() {
        val key = databaseKey(databasePath, existingKey = { fail() }, newKey = { NEW_KEY })

        assertEquals(NEW_KEY, key)
    }

    @Test
    fun givenADatabaseWithAStoredKey_whenGettingItsKey_thenTheStoredKeyIsUsed() {
        createDatabaseFile()

        val key = databaseKey(databasePath, existingKey = { STORED_KEY }, newKey = { fail() })

        assertEquals(STORED_KEY, key)
    }

    @Test
    fun givenADatabaseWithoutAStoredKey_whenGettingItsKey_thenItFailsInsteadOfCreatingOne() {
        createDatabaseFile()

        assertFailsWith<IllegalStateException> {
            databaseKey<String>(databasePath, existingKey = { null }, newKey = { fail() })
        }
    }

    private fun createDatabaseFile() {
        NSFileManager.defaultManager.createFileAtPath(databasePath, null, null)
    }

    private fun fail(): Nothing = throw AssertionError("Unexpected key source")

    private companion object {
        const val NEW_KEY = "new"
        const val STORED_KEY = "stored"
    }
}
