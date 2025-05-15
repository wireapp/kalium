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
package com.wire.backup.filesystem

import okio.Buffer
import okio.buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull


internal expect fun createTestStorage(): BackupPageStorage

class BackupPageStorageTest {

    private val entryStorage = createTestStorage()

    @BeforeTest
    fun setup() {
        entryStorage.clear()
    }

    @AfterTest
    fun dispose() {
        entryStorage.clear()
    }

    @Test
    fun givenNoEntry_whenReading_thenItShouldReturnNull() {
        val result = entryStorage["test.bin"]
        assertNull(result)
    }

    @Test
    fun givenNoEntryStored_whenListing_thenItShouldBeEmpty() {
        val result = entryStorage.listEntries()
        assertEquals(emptyList(), result)
    }

    @Test
    fun givenAnEntryStored_whenRetrievingIt_thenItShouldReturnOriginalData() {
        val expectedData = byteArrayOf(0x42)
        val entryData = Buffer().apply { write(expectedData) }
        val entryName = "test.bin"

        entryStorage.persistEntry(BackupPage(entryName, entryData))

        val result = entryStorage[entryName]
        assertNotNull(result)
        result.use {
            assertContentEquals(expectedData, it.buffer().readByteArray())
        }
    }

    @Test
    fun givenEntryAlreadyStored_whenAttemptingToStoreAgain_thenItShouldThrowException() {
        val expectedData = byteArrayOf(0x42)
        val entryData = Buffer().apply { write(expectedData) }
        val entryName = "test.bin"

        entryStorage.persistEntry(BackupPage(entryName, entryData))
        assertFailsWith<IllegalStateException> {
            entryStorage.persistEntry(BackupPage(entryName, entryData.copy()))
        }
    }

    @Test
    fun givenAnEntryStored_whenListingEntries_thenItShouldBePresent() {
        val expectedData = byteArrayOf(0x42)
        val entryData = Buffer().apply { write(expectedData) }
        val entryName = "test.bin"

        entryStorage.persistEntry(BackupPage(entryName, entryData))

        val result = entryStorage.listEntries()
        assertEquals(1, result.size)
        assertEquals(entryName, result.first().name)
        result.first().use {
            assertContentEquals(expectedData, it.buffer().readByteArray())
        }
    }

    @Test
    fun givenAnEntryStored_whenClearingStorage_thenItShouldNotBePresent() {
        val expectedData = byteArrayOf(0x42)
        val entryData = Buffer().apply { write(expectedData) }
        val entryName = "test.bin"

        entryStorage.persistEntry(BackupPage(entryName, entryData))
        entryStorage.clear()

        val result = entryStorage[entryName]
        assertNull(result)
    }
}
