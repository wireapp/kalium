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

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.createCompressedFile
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

typealias UserId = QualifiedID

@IgnoreIOS // TODO re-enable when backup support is implemented
class VerifyBackupUseCaseTest {

    @BeforeTest
    fun setup() {
        fakeFileSystem = FakeFileSystem()
        fakeKaliumFileSystem = FakeKaliumFileSystem()
    }

    @Test
    fun givenSomeCorrectCompressedEncryptedBackupFile_whenInvoked_thenReturnSuccessEncrypted() = runTest {
        // Given
        val encryptedDataFileName = "encryptedData.cc20"
        val encryptedDataPath = fakeKaliumFileSystem.tempFilePath(encryptedDataFileName)
        val encryptedData = encryptedDataPath.toString().encodeToByteArray()
        val compressedBackupFilePath = fakeKaliumFileSystem.tempFilePath("compressedEncryptedBackupFile.zip")
        val verifyBackup = Arrangement()
            .withPreStoredData(listOf(encryptedData to encryptedDataPath), compressedBackupFilePath)
            .arrange()

        // When
        val result = verifyBackup(compressedBackupFilePath)

        // Then
        assertTrue(result is VerifyBackupResult.Success)
        assertTrue(result.isEncrypted)
    }

    @Test
    fun givenSomeCorrectCompressedNonEncryptedBackupFile_whenInvoked_thenReturnSuccessNotEncrypted() = runTest {
        // Given
        val dbFileName = "unencryptedData.db"
        val dbPath = fakeKaliumFileSystem.tempFilePath(dbFileName)
        val dummyDBData = dbPath.toString().encodeToByteArray()
        val metadataFileName = BackupConstants.BACKUP_METADATA_FILE_NAME
        val metadataPath = fakeKaliumFileSystem.tempFilePath(metadataFileName)
        val metadataContent = metadataPath.toString().encodeToByteArray()
        val compressedBackupFilePath = fakeKaliumFileSystem.tempFilePath("compressedBackupFile.zip")
        val verifyBackup = Arrangement()
            .withPreStoredData(listOf(dummyDBData to dbPath, metadataContent to metadataPath), compressedBackupFilePath)
            .arrange()

        // When
        val result = verifyBackup(compressedBackupFilePath)

        // Then
        assertTrue(result is VerifyBackupResult.Success)
        assertFalse(result.isEncrypted)
    }

    @Test
    fun givenSomeIncorrectCompressedNonEncryptedBackupFile_whenInvoked_thenReturnFailureInvalidBackupFile() = runTest {
        // Given
        val wrongBackupFilePath = fakeKaliumFileSystem.tempFilePath("compressedBackupFile.weird")
        val weirdData = "Some weird data".encodeToByteArray()
        val verifyBackup = Arrangement()
            .withWrongPreStoredData(weirdData, wrongBackupFilePath)
            .arrange()

        // When
        val result = verifyBackup(wrongBackupFilePath)

        // Then
        assertTrue(result is VerifyBackupResult.Failure.InvalidBackupFile)
    }

    private class Arrangement {

        private var userId = UserId("some-user-id", "some-user-domain")

        @Suppress("NestedBlockDepth")
        fun withPreStoredData(data: List<Pair<ByteArray, Path>>, storedPath: Path) = apply {
            with(fakeKaliumFileSystem) {
                data.forEach { (rawData, dataPath) ->
                    sink(dataPath).buffer().use {
                        it.write(rawData)
                    }
                }
                val outputSink = sink(storedPath)
                createCompressedFile(data.map {
                    source(it.second) to it.second.name
                }, outputSink)
            }
            with(fakeFileSystem) {
                createDirectories(storedPath.parent ?: error("Parent path is null"))
                sink(storedPath).buffer().use { sink ->
                    sink.writeUtf8("Test file")
                }
            }
        }

        fun withWrongPreStoredData(data: ByteArray, storedPath: Path) = apply {
            with(fakeKaliumFileSystem) {
                sink(storedPath).buffer().use {
                    it.write(data)
                    it.close()
                }
            }
            with(fakeFileSystem) {
                createDirectories(storedPath.parent ?: error("Parent path is null"))
                sink(storedPath).buffer().use { sink ->
                    sink.writeUtf8("Test file")
                }
            }
        }

        fun arrange() = VerifyBackupUseCaseImpl(userId, fakeKaliumFileSystem)
    }

    companion object {
        var fakeKaliumFileSystem = FakeKaliumFileSystem()
        var fakeFileSystem = FakeFileSystem()
    }
}
