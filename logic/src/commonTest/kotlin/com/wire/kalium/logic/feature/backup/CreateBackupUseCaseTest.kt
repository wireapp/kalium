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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ENCRYPTED_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_METADATA_FILE_NAME
import com.wire.kalium.logic.framework.TestUser.SELF
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ExtractFilesParam
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.SecurityHelper
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.persistence.backup.DatabaseExporter
import com.wire.kalium.persistence.db.UserDBSecret
import io.ktor.util.decodeBase64Bytes
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@IgnoreIOS // TODO re-enable when BackupUtils is implemented on Darwin
@OptIn(ExperimentalCoroutinesApi::class)
class CreateBackupUseCaseTest {

    private val fakeFileSystem = FakeKaliumFileSystem()
    private val dispatcher = TestKaliumDispatcher

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatcher.default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenSomeValidData_whenCreatingNonEncryptedBackup_thenTheFinalBackupFileIsCreatedCorrectly() = runTest(dispatcher.default) {
        // Given
        val plainDB = "some-dummy-plain.db"
        val password = ""
        val selfUser = SELF.copy(handle = "self.handle")
        val currentDBData = "some-dummy.db".decodeBase64Bytes()
        val (arrangement, createBackupUseCase) = Arrangement()
            .withObservedClientId(ClientId("client-id"))
            .withExportedDB(plainDB, currentDBData)
            .withDeleteBackupDB(true)
            .withUserDBPassphrase(null)
            .withUserHandle(selfUser)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertTrue(result is CreateBackupResult.Success)
        assertTrue(result.backupFilePath.name.contains(".zip"))
        coVerify {
            arrangement.clientIdProvider.invoke()
        }.wasInvoked(once)

        // Check that there is a metadata file and a db file whose content is the same as the one we provided
        with(fakeFileSystem) {
            val extractedFilesPath = tempFilePath()
            createDirectory(extractedFilesPath)
            extractCompressedFile(source(result.backupFilePath), extractedFilesPath, ExtractFilesParam.All, fakeFileSystem)

            assertTrue(listDirectories(extractedFilesPath).firstOrNull { it.name == BACKUP_METADATA_FILE_NAME } != null)
            val extractedDB = listDirectories(extractedFilesPath).firstOrNull {
                it.name.contains(".db")
            }?.let {
                source(it).buffer().use { bufferedSource ->
                    bufferedSource.readByteArray()
                }
            }
            assertTrue(extractedDB.contentEquals(currentDBData))
        }
    }

    @Test
    fun givenSomeInvalidDBData_whenCreatingNonEncryptedBackup_thenTheRightErrorIsThrown() = runTest(dispatcher.default) {
        // Given
        val password = ""
        val selfUser = SELF.copy(handle = "self.handle")
        val (arrangement, createBackupUseCase) = Arrangement()
            .withUserDBPassphrase(null)
            .withExportedDBError()
            .withUserHandle(selfUser)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertTrue(result is CreateBackupResult.Failure)
        assertTrue(result.coreFailure is StorageFailure.DataNotFound)
        coVerify {
            arrangement.clientIdProvider.invoke()
        }.wasNotInvoked()
    }

    @Test
    fun givenSomeValidData_whenCreatingAnEncryptedBackup_thenTheFinalBackupFileIsCreatedCorrectly() = runTest(dispatcher.default) {
        // Given

        val plainDBFileLocation = "backup-encrypted.db"
        val password = "S0m3T0pS3CR3tP4\$\$w0rd"
        val dummyDBData = "some-dummy.db".decodeBase64Bytes()
        val selfUser = SELF.copy(handle = "self.handle")
        val (arrangement, createBackupUseCase) = Arrangement()
            .withObservedClientId(ClientId("client-id"))
            .withExportedDB(plainDBFileLocation, dummyDBData)
            .withDeleteBackupDB(true)
            .withUserDBPassphrase(null)
            .withUserHandle(selfUser)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertIs<CreateBackupResult.Success>(result)
        coVerify {
            arrangement.clientIdProvider.invoke()
        }.wasInvoked(once)

        // Check there is only one .cc20 file in the backup file
        with(fakeFileSystem) {
            val extractedFilesPath = tempFilePath()
            createDirectory(extractedFilesPath)
            extractCompressedFile(source(result.backupFilePath), extractedFilesPath, ExtractFilesParam.All, fakeFileSystem)
            val extractedDBPath = listDirectories(extractedFilesPath).firstOrNull { it.name.contains(".cc20") }
            assertEquals(BACKUP_ENCRYPTED_FILE_NAME, extractedDBPath?.name)
        }
    }

    @Suppress("NestedBlockDepth")
    private inner class Arrangement {
        private var userId = UserId("some-user-id", "some-user-domain")

        @Mock
        val clientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val databaseExporter = mock(DatabaseExporter::class)

        @Mock
        val securityHelper = mock(SecurityHelper::class)

        fun withUserDBPassphrase(passphrase: UserDBSecret?) = apply {
            every {
                securityHelper.userDBOrSecretNull(any())
            }.returns(passphrase)
        }

        suspend fun withObservedClientId(clientId: ClientId?) = apply {
            coEvery {
                clientIdProvider.invoke()
            }.returns(clientId?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound))
        }

        fun withExportedDB(dbName: String?, dbData: ByteArray) = apply {
            with(fakeFileSystem) {
                val exportedDBPath = dbName?.let { rootDBPath / it } ?: "null".toPath()
                sink(exportedDBPath).buffer().use { it.write(dbData) }
                every {
                    databaseExporter.exportToPlainDB(any())
                }.returns(exportedDBPath.toString())
            }
        }

        fun withExportedDBError() = apply {
            every {
                databaseExporter.exportToPlainDB(any())
            }.returns(null)
        }

        fun withDeleteBackupDB(result: Boolean) = apply {
            every {
                databaseExporter.deleteBackupDBFile()
            }.returns(result)
        }

        suspend fun withUserHandle(selfUser: SelfUser) = apply {
            coEvery {
                userRepository.getSelfUser()
            }.returns(selfUser.right())
        }

        fun arrange(): Pair<Arrangement, CreateBackupUseCase> =
            this to CreateBackupUseCaseImpl(
                userId,
                clientIdProvider,
                userRepository,
                fakeFileSystem,
                databaseExporter,
                securityHelper,
                dispatcher
            )
    }
}
