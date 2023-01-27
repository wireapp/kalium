/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ENCRYPTED_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_METADATA_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ZIP_FILE_NAME
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.persistence.backup.DatabaseExporter
import io.ktor.util.decodeBase64Bytes
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateBackupUseCaseTest {

    private val fakeFileSystem = FakeKaliumFileSystem()
    private val dispatcher = TestKaliumDispatcher

    @Test
    fun givenSomeValidData_whenCreatingNonEncryptedBackup_thenTheFinalBackupFileIsCreatedCorrectly() = runTest(dispatcher.default) {
        // Given
        val plainDB = "some-dummy-plain.db"
        val password = ""
        val currentDB = "some-dummy.db".decodeBase64Bytes()
        val (arrangement, createBackupUseCase) = Arrangement()
            .withObservedClientId(ClientId("client-id"))
            .withExportedDB(plainDB)
            .withDeleteBackupDB(true)
            .withProvidedDB(currentDB)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertTrue(result is CreateBackupResult.Success)
        assertEquals(result.backupFilePath.name, BACKUP_ZIP_FILE_NAME)
        verify(arrangement.clientIdProvider)
            .suspendFunction(arrangement.clientIdProvider::invoke)
            .wasInvoked(once)

        // Check that there is a metadata file and a db file whose content is the same as the one we provided
        with(fakeFileSystem) {
            val extractedFilesPath = tempFilePath()
            createDirectory(extractedFilesPath)
            extractCompressedFile(source(result.backupFilePath), extractedFilesPath, fakeFileSystem)

            assertTrue(listDirectories(extractedFilesPath).firstOrNull { it.name == BACKUP_METADATA_FILE_NAME } != null)
            assertTrue(listDirectories(extractedFilesPath).firstOrNull {
                it.name.contains(".db")
            }?.let {
                source(it).buffer().use { bufferedSource ->
                    bufferedSource.readByteArray()
                }
            }.contentEquals(currentDB))
        }
    }

    @Test
    fun givenSomeInvalidDBData_whenCreatingNonEncryptedBackup_thenTheRightErrorIsThrown() = runTest(dispatcher.default) {
        // Given
        val password = ""
        val dummyDB = null
        val (arrangement, createBackupUseCase) = Arrangement()
            .withExportedDB(dummyDB)
            .withProvidedDB(dummyDB)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertTrue(result is CreateBackupResult.Failure)
        assertTrue(result.coreFailure is StorageFailure.DataNotFound)
        verify(arrangement.clientIdProvider)
            .suspendFunction(arrangement.clientIdProvider::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenSomeValidData_whenCreatingAnEncryptedBackup_thenTheFinalBackupFileIsCreatedCorrectly() = runTest(dispatcher.default) {
        // Given

        val plainDBFileLocation = "backup-encrypted.db"
        val password = "S0m3T0pS3CR3tP4\$\$w0rd"
        val dummyDB = "some-dummy.db".decodeBase64Bytes()
        val (arrangement, createBackupUseCase) = Arrangement()
            .withObservedClientId(ClientId("client-id"))
            .withExportedDB(plainDBFileLocation)
            .withDeleteBackupDB(true)
            .withProvidedDB(dummyDB)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertIs<CreateBackupResult.Success>(result)
        verify(arrangement.clientIdProvider)
            .suspendFunction(arrangement.clientIdProvider::invoke)
            .wasInvoked(once)

        // Check there is only one .cc20 file in the backup file
        with(fakeFileSystem) {
            val extractedFilesPath = tempFilePath()
            createDirectory(extractedFilesPath)
            extractCompressedFile(source(result.backupFilePath), extractedFilesPath, fakeFileSystem)
            val result = listDirectories(extractedFilesPath).firstOrNull()
            assertEquals(BACKUP_ENCRYPTED_FILE_NAME, result?.name)
        }
    }

    @Suppress("NestedBlockDepth")
    private inner class Arrangement {
        private var userId = UserId("some-user-id", "some-user-domain")

        @Mock
        val clientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val databaseExporter = mock(classOf<DatabaseExporter>())
        fun withObservedClientId(clientId: ClientId?) = apply {
            given(clientIdProvider)
                .suspendFunction(clientIdProvider::invoke)
                .whenInvoked()
                .then {
                    clientId?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
                }
        }

        fun withProvidedDB(dbData: ByteArray?) = apply {
            with(fakeFileSystem) {
                dbData?.let { rawData ->
                    sink(rootDBPath).buffer().use { it.write(rawData) }
                }
            }
        }

        fun withExportedDB(result: String?) = apply {
            with(fakeFileSystem) {
                result?.let { rawData ->
                    sink(rootDBPath).buffer().use { it.write(rawData.toByteArray()) }
                }
            }
            given(databaseExporter)
                .function(databaseExporter::exportToPlainDB)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withDeleteBackupDB(result: Boolean, throwable: Throwable? = null) = apply {
            if (throwable != null) given(databaseExporter)
                .function(databaseExporter::deleteBackupDBFile)
                .whenInvoked()
                .thenThrow(throwable)
            else given(databaseExporter)
                .function(databaseExporter::deleteBackupDBFile)
                .whenInvoked()
                .thenReturn(result)
        }

        fun arrange(): Pair<Arrangement, CreateBackupUseCase> =
            this to CreateBackupUseCaseImpl(
                userId,
                clientIdProvider,
                fakeFileSystem,
                databaseExporter,
                dispatcher
            )

    }

}
