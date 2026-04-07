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

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.createCompressedFile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Path
import okio.buffer
import okio.fakefilesystem.FakeFileSystem
import okio.use
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@IgnoreIOS // TODO re-enable when backup support is implemented
class ExtractCryptoStateUseCaseTest {

    private lateinit var fakeFileSystem: FakeFileSystem
    private lateinit var fakeKaliumFileSystem: FakeKaliumFileSystem

    @BeforeTest
    fun setup() {
        fakeFileSystem = FakeFileSystem()
        fakeKaliumFileSystem = FakeKaliumFileSystem(fakeFileSystem)
    }

    @Test
    fun givenValidCompressedBackupWithAllFiles_whenInvoked_thenReturnSuccess() = runTest {
        // Given
        val arrangement = Arrangement()
        val (backupFilePath, metadata) = arrangement
            .withValidCryptoStateBackup()
            .arrange()

        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(backupFilePath)

        // Then
        assertIs<ExtractCryptoStateResult.Success>(result)
        assertTrue(fakeKaliumFileSystem.exists(result.extractedDir))
        assertTrue(fakeKaliumFileSystem.exists(result.mlsKeystorePath))
        assertTrue(fakeKaliumFileSystem.exists(result.proteusKeystorePath))
        assertEquals(result.metadata.version, metadata.version)
    }

    @Test
    fun givenBackupFileThatDoesNotExist_whenInvoked_thenReturnFailure() = runTest {
        // Given
        val nonExistentPath = fakeKaliumFileSystem.tempFilePath("non_existent_backup.zip")
        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(nonExistentPath)

        // Then
        assertIs<ExtractCryptoStateResult.Failure>(result)
        assertIs<CoreFailure.Unknown>(result.error)
    }

    @Test
    fun givenBackupMissingMLSKeystore_whenInvoked_thenReturnFailure() = runTest {
        // Given
        val arrangement = Arrangement()
        val backupFilePath = arrangement
            .withBackupMissingFile(ExtractCryptoStateUseCaseImpl.MLS_KEYSTORE_NAME)
            .arrange()
            .first

        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(backupFilePath)

        // Then
        assertIs<ExtractCryptoStateResult.Failure>(result)
        assertIs<CoreFailure.Unknown>(result.error)
    }

    @Test
    fun givenBackupMissingProteusKeystore_whenInvoked_thenReturnFailure() = runTest {
        // Given
        val arrangement = Arrangement()
        val backupFilePath = arrangement
            .withBackupMissingFile(ExtractCryptoStateUseCaseImpl.PROTEUS_KEYSTORE_NAME)
            .arrange()
            .first

        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(backupFilePath)

        // Then
        assertIs<ExtractCryptoStateResult.Failure>(result)
        assertIs<CoreFailure.Unknown>(result.error)
    }

    @Test
    fun givenBackupMissingMetadata_whenInvoked_thenReturnFailure() = runTest {
        // Given
        val arrangement = Arrangement()
        val backupFilePath = arrangement
            .withBackupMissingFile(BackupConstants.BACKUP_METADATA_FILE_NAME)
            .arrange()
            .first

        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(backupFilePath)

        // Then
        assertIs<ExtractCryptoStateResult.Failure>(result)
        assertIs<CoreFailure.Unknown>(result.error)
    }

    @Test
    fun givenBackupWithInvalidMetadata_whenInvoked_thenReturnFailure() = runTest {
        // Given
        val arrangement = Arrangement()
        val backupFilePath = arrangement
            .withBackupInvalidMetadata()
            .arrange()
            .first

        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(backupFilePath)

        // Then
        assertIs<ExtractCryptoStateResult.Failure>(result)
        assertIs<CoreFailure.Unknown>(result.error)
    }

    @Test
    fun givenValidBackup_whenInvoked_thenExtractedDirContainsAllFiles() = runTest {
        // Given
        val arrangement = Arrangement()
        val (backupFilePath, _) = arrangement
            .withValidCryptoStateBackup()
            .arrange()

        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(backupFilePath)

        // Then
        assertIs<ExtractCryptoStateResult.Success>(result)
        val extractedDir = result.extractedDir
        assertTrue(fakeKaliumFileSystem.exists(extractedDir.resolve(ExtractCryptoStateUseCaseImpl.MLS_KEYSTORE_NAME)))
        assertTrue(fakeKaliumFileSystem.exists(extractedDir.resolve(ExtractCryptoStateUseCaseImpl.PROTEUS_KEYSTORE_NAME)))
        assertTrue(fakeKaliumFileSystem.exists(extractedDir.resolve(BackupConstants.BACKUP_METADATA_FILE_NAME)))
    }

    @Test
    fun givenValidBackup_whenInvoked_thenMetadataIsParsedCorrectly() = runTest {
        // Given
        val arrangement = Arrangement()
        val (backupFilePath, originalMetadata) = arrangement
            .withValidCryptoStateBackup()
            .arrange()

        val useCase = ExtractCryptoStateUseCaseImpl(fakeKaliumFileSystem)

        // When
        val result = useCase.invoke(backupFilePath)

        // Then
        assertIs<ExtractCryptoStateResult.Success>(result)
        assertEquals(result.metadata.version, originalMetadata.version)
    }

    private inner class Arrangement {

        private var shouldOmitFile: String? = null
        private var shouldInvalidateMetadata: Boolean = false

        fun withValidCryptoStateBackup() = apply {
            shouldOmitFile = null
            shouldInvalidateMetadata = false
        }

        fun withBackupMissingFile(fileName: String) = apply {
            shouldOmitFile = fileName
        }

        fun withBackupInvalidMetadata() = apply {
            shouldInvalidateMetadata = true
        }

        fun arrange(): Pair<Path, CryptoStateBackupMetadata> {
            val metadata = CryptoStateBackupMetadata(
                version = CryptoStateBackupMetadata.CURRENT_VERSION,
                clientId = "test-client-id",
                mlsDbPassphrase = "mls-passphrase",
                proteusDbPassphrase = "proteus-passphrase"
            )
            val mlsKeystoreData = "mls-keystore-content".encodeToByteArray()
            val proteusKeystoreData = "proteus-keystore-content".encodeToByteArray()

            val mlsKeystorePath = fakeKaliumFileSystem.tempFilePath(ExtractCryptoStateUseCaseImpl.MLS_KEYSTORE_NAME)
            val proteusKeystorePath = fakeKaliumFileSystem.tempFilePath(ExtractCryptoStateUseCaseImpl.PROTEUS_KEYSTORE_NAME)
            val metadataPath = fakeKaliumFileSystem.tempFilePath(BackupConstants.BACKUP_METADATA_FILE_NAME)

            if (shouldOmitFile != ExtractCryptoStateUseCaseImpl.MLS_KEYSTORE_NAME) {
                fakeKaliumFileSystem.sink(mlsKeystorePath).buffer().use {
                    it.write(mlsKeystoreData)
                }
            }

            if (shouldOmitFile != ExtractCryptoStateUseCaseImpl.PROTEUS_KEYSTORE_NAME) {
                fakeKaliumFileSystem.sink(proteusKeystorePath).buffer().use {
                    it.write(proteusKeystoreData)
                }
            }

            if (shouldOmitFile != BackupConstants.BACKUP_METADATA_FILE_NAME) {
                val metadataContent = if (shouldInvalidateMetadata) {
                    "invalid json content {{"
                } else {
                    Json.encodeToString(CryptoStateBackupMetadata.serializer(), metadata)
                }
                fakeKaliumFileSystem.sink(metadataPath).buffer().use {
                    it.writeUtf8(metadataContent)
                }
            }

            val backupFilePath = fakeKaliumFileSystem.tempFilePath("crypto_state_backup.zip")
            val filesToCompress = mutableListOf<Pair<okio.Source, String>>()

            if (shouldOmitFile != ExtractCryptoStateUseCaseImpl.MLS_KEYSTORE_NAME) {
                filesToCompress.add(
                    fakeKaliumFileSystem.source(mlsKeystorePath) to ExtractCryptoStateUseCaseImpl.MLS_KEYSTORE_NAME
                )
            }

            if (shouldOmitFile != ExtractCryptoStateUseCaseImpl.PROTEUS_KEYSTORE_NAME) {
                filesToCompress.add(
                    fakeKaliumFileSystem.source(proteusKeystorePath) to ExtractCryptoStateUseCaseImpl.PROTEUS_KEYSTORE_NAME
                )
            }

            if (shouldOmitFile != BackupConstants.BACKUP_METADATA_FILE_NAME) {
                filesToCompress.add(
                    fakeKaliumFileSystem.source(metadataPath) to BackupConstants.BACKUP_METADATA_FILE_NAME
                )
            }

            val outputSink = fakeKaliumFileSystem.sink(backupFilePath)
            createCompressedFile(filesToCompress, outputSink)

            // Clean up temporary files
            if (shouldOmitFile != ExtractCryptoStateUseCaseImpl.MLS_KEYSTORE_NAME) {
                fakeKaliumFileSystem.delete(mlsKeystorePath)
            }
            if (shouldOmitFile != ExtractCryptoStateUseCaseImpl.PROTEUS_KEYSTORE_NAME) {
                fakeKaliumFileSystem.delete(proteusKeystorePath)
            }
            if (shouldOmitFile != BackupConstants.BACKUP_METADATA_FILE_NAME) {
                fakeKaliumFileSystem.delete(metadataPath)
            }

            return backupFilePath to metadata
        }
    }
}
