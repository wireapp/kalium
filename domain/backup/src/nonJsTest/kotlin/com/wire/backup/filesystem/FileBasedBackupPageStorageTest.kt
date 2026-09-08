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
package com.wire.backup.filesystem

import okio.Buffer
import okio.FileHandle
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Source
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileBasedBackupPageStorageTest {

    private val delegateFileSystem = FakeFileSystem()
    private val recordingFileSystem = RecordingFileSystem(delegateFileSystem)
    private val workDirectory = "/backup".toPath()
    private val storage = FileBasedBackupPageStorage(recordingFileSystem, workDirectory, true)

    @AfterTest
    fun tearDown() {
        delegateFileSystem.checkNoOpenFiles()
    }

    @Test
    fun givenStoredEntry_whenRetrievingByName_thenUsesFileSystemSource() {
        val entryName = "users_0.binpb"
        val expectedData = byteArrayOf(0x42)
        storage.persistEntry(BackupPage(entryName, Buffer().write(expectedData)))
        recordingFileSystem.clearRecordedReads()

        val page = assertNotNull(storage[entryName])
        page.use { source ->
            assertContentEquals(expectedData, source.buffer().readByteArray())
        }

        assertEquals(listOf(workDirectory / entryName), recordingFileSystem.sourcePaths)
        assertEquals(emptyList(), recordingFileSystem.openReadOnlyPaths)
    }

    @Test
    fun givenStoredEntry_whenListingEntries_thenUsesFileSystemSource() {
        val entryName = "users_0.binpb"
        val expectedData = byteArrayOf(0x42)
        storage.persistEntry(BackupPage(entryName, Buffer().write(expectedData)))
        recordingFileSystem.clearRecordedReads()

        val page = storage.listEntries().single()
        page.use { source ->
            assertContentEquals(expectedData, source.buffer().readByteArray())
        }

        assertEquals(listOf(workDirectory / entryName), recordingFileSystem.sourcePaths)
        assertEquals(emptyList(), recordingFileSystem.openReadOnlyPaths)
    }

    private class RecordingFileSystem(delegate: FakeFileSystem) : ForwardingFileSystem(delegate) {
        val sourcePaths = mutableListOf<Path>()
        val openReadOnlyPaths = mutableListOf<Path>()

        override fun source(file: Path): Source {
            sourcePaths += file
            return super.source(file)
        }

        override fun openReadOnly(file: Path): FileHandle {
            openReadOnlyPaths += file
            return super.openReadOnly(file)
        }

        fun clearRecordedReads() {
            sourcePaths.clear()
            openReadOnlyPaths.clear()
        }
    }
}
