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
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.createCompressedFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@IgnoreIOS // TODO re-enable when backup support is implemented
@OptIn(ExperimentalCoroutinesApi::class)
class VerifyBackupUseCaseTest {

    @BeforeTest
    fun setup() {
        fakeFileSystem = FakeKaliumFileSystem()
    }

    @Test
    fun givenSomeCorrectCompressedEncryptedBackupFile_whenInvoked_thenReturnSuccessEncrypted() = runTest {
        // Given
        val encryptedDataFileName = "encryptedData.cc20"
        val encryptedDataPath = fakeFileSystem.tempFilePath(encryptedDataFileName)
        val encryptedData = encryptedDataPath.toString().encodeToByteArray()
        val compressedBackupFilePath = fakeFileSystem.tempFilePath("compressedEncryptedBackupFile.zip")
        val verifyBackup = Arrangement()
            .withPreStoredData(listOf(encryptedData to encryptedDataPath), compressedBackupFilePath)
            .arrange()

        // When
        val result = verifyBackup(compressedBackupFilePath)

        // Then
        assertTrue(result is VerifyBackupResult.Success.Encrypted)
    }

    @Test
    fun givenSomeCorrectCompressedNonEncryptedBackupFile_whenInvoked_thenReturnSuccessNotEncrypted() = runTest {
        // Given
        val dbFileName = "unencryptedData.db"
        val dbPath = fakeFileSystem.tempFilePath(dbFileName)
        val dummyDBData = dbPath.toString().encodeToByteArray()
        val metadataFileName = BackupConstants.BACKUP_METADATA_FILE_NAME
        val metadataPath = fakeFileSystem.tempFilePath(metadataFileName)
        val metadataContent = metadataPath.toString().encodeToByteArray()
        val compressedBackupFilePath = fakeFileSystem.tempFilePath("compressedBackupFile.zip")
        val verifyBackup = Arrangement()
            .withPreStoredData(listOf(dummyDBData to dbPath, metadataContent to metadataPath), compressedBackupFilePath)
            .arrange()

        // When
        val result = verifyBackup(compressedBackupFilePath)

        // Then
        assertTrue(result is VerifyBackupResult.Success.NotEncrypted)
    }

    @Test
    fun givenSomeIncorrectCompressedNonEncryptedBackupFile_whenInvoked_thenReturnFailureInvalidBackupFile() = runTest {
        // Given
        val wrongBackupFilePath = fakeFileSystem.tempFilePath("compressedBackupFile.weird")
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
        @Suppress("NestedBlockDepth")
        fun withPreStoredData(data: List<Pair<ByteArray, Path>>, storedPath: Path) = apply {
            with(fakeFileSystem) {
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
        }

        fun withWrongPreStoredData(data: ByteArray, storedPath: Path) = apply {
            with(fakeFileSystem) {
                sink(storedPath).buffer().use {
                    it.write(data)
                    it.close()
                }
            }
        }

        fun arrange() = VerifyBackupUseCaseImpl(fakeFileSystem)
    }

    companion object {
        var fakeFileSystem = FakeKaliumFileSystem()
    }
}
