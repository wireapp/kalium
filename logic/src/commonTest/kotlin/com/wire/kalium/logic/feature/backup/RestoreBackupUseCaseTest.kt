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

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.utils.ChaCha20Encryptor.encryptBackupFile
import com.wire.kalium.logic.clientPlatform
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.persistence.db.UserDBSecret
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

@IgnoreIOS // TODO re-enable when BackupUtils is implemented on Darwin
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreBackupUseCaseTest {

    private val fakeFileSystem = FakeKaliumFileSystem()

    @Test
    fun givenACorrectNonEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredSuccessfully() = runTest {
        // given
        val selfUser = TestUser.SELF.copy(handle = "self.handle")
        val backupPath = fakeFileSystem.tempFilePath("backup.zip")
        val (arrangement, useCase) = Arrangement()
            .withCurrentClientId(currentTestClientId)
            .withUnencryptedBackup(backupPath, currentTestUserId)
            .withCorrectDbImportAction()
            .withSelfUser(selfUser)
            .arrange()

        // when
        val result = useCase(backupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Success)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasInvoked(once)
    }

    @Test
    fun givenACorrectNonEncryptedBackupFileWithWrongAuthor_whenRestoring_thenTheCorrectErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.zip")
        val selfUser = TestUser.SELF.copy(handle = "self.handle")
        val (arrangement, useCase) = Arrangement()
            .withCurrentClientId(currentTestClientId)
            .withUnencryptedBackup(backupPath, UserId("wrongUserId", "wrongDomain"))
            .withCorrectDbImportAction()
            .withSelfUser(selfUser)
            .arrange()

        // when
        val result = useCase(backupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectNonEncryptedBackupFileWithWrongMetadataFileName_whenRestoring_thenTheCorrectErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.zip")
        val (arrangement, useCase) = Arrangement()
            .withCurrentClientId(currentTestClientId)
            .withUnencryptedBackup(backupPath, currentTestUserId, true)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenAValidEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredCorrectly() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val selfUser = TestUser.SELF.copy(handle = "self.handle")
        val (arrangement, useCase) = Arrangement()
            .withCurrentClientId(currentTestClientId)
            .withEncryptedBackup(backupPath, currentTestUserId, password)
            .withSelfUser(selfUser)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Success)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasInvoked(once)
    }

    @Ignore
    @Test
    fun givenAnEncryptedBackupFileFromDifferentUserID_whenRestoring_thenTheRightErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val selfUser = TestUser.SELF.copy(handle = "self.handle")
        val (arrangement, useCase) = Arrangement()
            .withCurrentClientId(currentTestClientId)
            .withEncryptedBackup(backupPath, UserId("Darth-Vader", "death-star"), password)
            .withCorrectDbImportAction()
            .withSelfUser(selfUser)
            .arrange()

        // when
        val result = useCase(backupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Ignore
    @Test
    fun givenACorrectlyEncryptedBackup_whenRestoringWithWrongPassword_thenTheRightErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val selfUser = TestUser.SELF.copy(handle = "self.handle")
        val (arrangement, useCase) = Arrangement()
            .withCurrentClientId(currentTestClientId)
            .withEncryptedBackup(backupPath, currentTestUserId, password)
            .withCorrectDbImportAction()
            .withSelfUser(selfUser)
            .arrange()

        // when
        val result = useCase(backupPath, "OhIForgotMyPassword")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidPassword)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectlyEncryptedBackup_whenRestoringWithADBImportError_thenTheRightErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val selfUser = TestUser.SELF.copy(handle = "self.handle")
        val (arrangement, useCase) = Arrangement()
            .withCurrentClientId(currentTestClientId)
            .withEncryptedBackup(backupPath, currentTestUserId, password)
            .withIncorrectDbImportAction()
            .withSelfUser(selfUser)
            .arrange()

        // when
        val result = useCase(backupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.BackupIOFailure)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasInvoked(once)
    }

    private inner class Arrangement {

        @Mock
        val databaseImporter = mock(classOf<DatabaseImporter>())

        @Mock
        val restoreWebBackupUseCase = mock(classOf<RestoreWebBackupUseCase>())

        @Mock
        val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        val fakeDBFileName = "fakeDBFile.db"
        private val selfUserId = currentTestUserId
        private val fakeDBData = fakeDBFileName.encodeToByteArray()
        private val idMapper = MapperProvider.idMapper()

        private fun createMetadataFile(metadataFilePath: Path, userId: UserId, userDBSecret: UserDBSecret? = null): Path {
            val clientId = "dummy-client-id"
            val creationTime = DateTimeUtil.currentIsoDateTimeString()
            val metadataJson = Json.encodeToString(
                BackupMetadata(
                    clientPlatform,
                    BackupCoder.version,
                    userId.toString(),
                    creationTime,
                    clientId
                )
            )
            fakeFileSystem.sink(metadataFilePath).buffer().use {
                it.write(metadataJson.encodeToByteArray())
            }
            return metadataFilePath
        }

        private fun createCompressedBackup(
            path: Path,
            userId: UserId,
            withWrongMetadataFile: Boolean = false,
            userDBSecret: UserDBSecret? = null,
        ) {
            with(fakeFileSystem) {
                val metadataFileName = if (withWrongMetadataFile) "wrong-metadata.json" else BackupConstants.BACKUP_METADATA_FILE_NAME
                val metadataPath = tempFilePath(metadataFileName)
                createMetadataFile(metadataPath, userId, userDBSecret)
                val dbPath = tempFilePath(fakeDBFileName)
                sink(dbPath).buffer().use { it.write(fakeDBData) }
                val outputSink = sink(path)
                createCompressedFile(listOf(source(metadataPath) to metadataPath.name, source(dbPath) to dbPath.name), outputSink)
            }
        }

        private suspend fun createEncryptedBackup(
            encryptedBackupPath: Path,
            userId: UserId,
            password: String,
            withWrongMetadataFile: Boolean = false,
            userDBSecret: UserDBSecret? = null,
        ): Path = with(fakeFileSystem) {
            val compressedBackupFilePath = fakeFileSystem.tempFilePath("backup.zip")
            createCompressedBackup(compressedBackupFilePath, userId, withWrongMetadataFile, userDBSecret)

            val cryptoUserId = idMapper.toCryptoModel(userId)
            val coder = BackupCoder(cryptoUserId, Passphrase(password))
            val inputSource = source(compressedBackupFilePath)
            val outputSink = sink(encryptedBackupPath)

            encryptBackupFile(inputSource, outputSink, cryptoUserId, coder.passphrase)
            return encryptedBackupPath
        }

        fun withUnencryptedBackup(
            path: Path,
            userId: UserId,
            withWrongMetadataFile: Boolean = false,
            userDBSecret: UserDBSecret? = null,
        ) = apply {
            createCompressedBackup(path, userId, withWrongMetadataFile, userDBSecret)
        }

        suspend fun withEncryptedBackup(path: Path, userId: UserId, password: String) = apply {
            with(fakeFileSystem) {
                val encryptedBackupPath = fakeFileSystem.tempFilePath("backup.cc20")
                createEncryptedBackup(encryptedBackupPath, userId, password)
                val outputSink = sink(path)
                createCompressedFile(listOf(source(encryptedBackupPath) to encryptedBackupPath.name), outputSink)
            }
        }

        fun withCorrectDbImportAction(userDBSecret: UserDBSecret? = null) = apply {
            given(databaseImporter)
                .suspendFunction(databaseImporter::importFromFile)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withIncorrectDbImportAction(userDBSecret: UserDBSecret? = null) = apply {
            given(databaseImporter)
                .suspendFunction(databaseImporter::importFromFile)
                .whenInvokedWith(any(), any())
                .thenThrow(RuntimeException("DB import failed"))
        }

        fun withCurrentClientId(clientId: ClientId = currentTestClientId) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))
        }

        fun withSelfUser(selfUser: SelfUser) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getSelfUser)
                .whenInvoked()
                .thenReturn(selfUser)
        }

        fun arrange() = this to RestoreBackupUseCaseImpl(
            databaseImporter = databaseImporter,
            kaliumFileSystem = fakeFileSystem,
            userId = selfUserId,
            userRepository = userRepository,
            currentClientIdProvider = currentClientIdProvider,
            idMapper = idMapper,
            restoreWebBackup = restoreWebBackupUseCase
        )
    }

    companion object {
        val currentTestUserId = UserId("some-user-id", "some-domain")
        val currentTestClientId = ClientId("some-client-id")
    }
}
