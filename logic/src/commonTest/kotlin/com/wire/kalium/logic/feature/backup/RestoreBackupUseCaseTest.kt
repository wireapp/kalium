package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.utils.ChaCha20Encryptor.encryptBackupFile
import com.wire.kalium.logic.clientPlatform
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageUseCaseTest
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.persistence.db.UserDBSecret
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreBackupUseCaseTest {

    @Test
    fun givenACorrectNonEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredSuccessfully() = runTest {
        // given
        val extractedBackupPath = fakeFileSystem.tempFilePath("extractedBackupPath")
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(extractedBackupPath, currentTestUserId)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(extractedBackupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Success)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(eq((extractedBackupPath / arrangement.fakeDBFileName).toString()), eq(null))
            .wasInvoked(once)
    }

    @Test
    fun givenACorrectNonEncryptedBackupFileWithWrongAuthor_whenRestoring_thenTheCorrectErrorIsThrown() = runTest {
        // given
        val extractedBackupPath = fakeFileSystem.tempFilePath("extractedBackupPath")
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(extractedBackupPath, UserId("wrongUserId", "wrongDomain"))
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(extractedBackupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(eq((extractedBackupPath / arrangement.fakeDBFileName).toString()), eq(null))
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectNonEncryptedBackupFileWithWrongMetadataFileName_whenRestoring_thenTheCorrectErrorIsThrown() = runTest {
        // given
        val extractedBackupPath = fakeFileSystem.tempFilePath("extractedBackupPath")
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(
                unencryptedDBPath = extractedBackupPath,
                userId = currentTestUserId,
                withWrongMetadataFile = true
            )
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(extractedBackupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(eq((extractedBackupPath / arrangement.fakeDBFileName).toString()), eq(null))
            .wasNotInvoked()
    }

    @Test
    fun givenAValidEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredCorrectly() = runTest {
        // given
        val extractedBackupPath = fakeFileSystem.tempFilePath("extractedBackupPath")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(extractedBackupPath, currentTestUserId, password)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(extractedBackupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Success)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasInvoked(once)
    }

    @Test
    fun givenAnEncryptedBackupFileFromDifferentUserID_whenRestoring_thenTheRightErrorIsThrown() = runTest {
        // given
        val extractedBackupPath = fakeFileSystem.tempFilePath("extractedBackupPath")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(extractedBackupPath, UserId("Darth-Vader", "death-star"), password)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(extractedBackupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(eq((extractedBackupPath / arrangement.fakeDBFileName).toString()), any())
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectlyEncryptedBackup_whenRestoringWithWrongPassword_thenTheRightErrorIsThrown() = runTest {
        // given
        val extractedBackupPath = fakeFileSystem.tempFilePath("extractedBackupPath")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(extractedBackupPath, currentTestUserId, password)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(extractedBackupPath, "OhIForgotMyPassword")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidPassword)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(eq((extractedBackupPath / arrangement.fakeDBFileName).toString()), any())
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectlyEncryptedBackup_whenRestoringWithADBImportError_thenTheRightErrorIsThrown() = runTest {
        // given
        val extractedBackupPath = fakeFileSystem.tempFilePath("extractedBackupPath")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(extractedBackupPath, currentTestUserId, password)
            .withIncorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(extractedBackupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.BackupIOFailure)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), any())
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val databaseImporter = mock(classOf<DatabaseImporter>())

        val fakeDBFileName = "fakeDBFile.db"
        private val selfUserId = currentTestUserId
        private val fakeDBData = fakeDBFileName.encodeToByteArray()
        private val encryptedBackupName = "encryptedDB.cc20"
        private val idMapper = MapperProvider.idMapper()

        private fun createMetadataFile(userId: UserId): Path {
            val clientId = "dummy-client-id"
            val userDBSecret = UserDBSecret("dummy-user-db-secret".decodeBase64Bytes())
            val creationTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
            val metadataJson =
                BackupMetadata(
                    clientPlatform,
                    BackupCoder.version,
                    userId.toString(),
                    creationTime,
                    clientId,
                    userDBSecret.value.encodeBase64(),
                    true
                ).toString()
            val metadataFilePath = fakeFileSystem.tempFilePath(BackupConstants.BACKUP_METADATA_FILE_NAME)
            fakeFileSystem.sink(metadataFilePath).buffer().use {
                it.write(metadataJson.encodeToByteArray())
            }
            return metadataFilePath
        }

        private suspend fun encryptData(extractedBackupRootPath: Path, dbData: ByteArray, userId: UserId, password: String): Path =
            with(fakeFileSystem) {
                val cryptoUserId = idMapper.toCryptoModel(userId)
                val coder = BackupCoder(cryptoUserId, Passphrase(password))
                val dbPath = fakeFileSystem.rootDBPath
                sink(dbPath).buffer().use {
                    it.write(dbData)
                }
                val backupPath = extractedBackupRootPath / "backup.zip"
                val backupMetadataPath = createMetadataFile(userId)
                val encryptedBackupPath = extractedBackupRootPath / "encryptedBackup.zip"
                val backupSink = sink(backupPath)
                createCompressedFile(
                    listOf(
                        source(backupMetadataPath) to BackupConstants.BACKUP_METADATA_FILE_NAME,
                        source(dbPath) to BackupConstants.BACKUP_USER_DB_NAME
                    ), backupSink
                )
                val inputSource = source(backupPath)
                val outputSink = sink(encryptedBackupPath)
                encryptBackupFile(inputSource, outputSink, cryptoUserId, coder.passphrase)

                return encryptedBackupPath
            }

        fun withUnencryptedBackup(unencryptedDBPath: Path, userId: UserId, withWrongMetadataFile: Boolean = false) = apply {
            val metadataJson =
                BackupMetadata(
                    clientPlatform,
                    BackupCoder.version,
                    userId.toString(),
                    "7-12-2022:14:00:00",
                    "some-client-id",
                    "some-user-db-secret",
                    true
                ).toString()
            val metadataFilePath = if (withWrongMetadataFile) "wrong-metadata.json" else BackupConstants.BACKUP_METADATA_FILE_NAME
            fakeFileSystem.createDirectory(unencryptedDBPath)
            fakeFileSystem.sink(unencryptedDBPath / fakeDBFileName).buffer().use {
                it.write(fakeDBData)
            }
            fakeFileSystem.sink(unencryptedDBPath / metadataFilePath).buffer().use {
                it.write(metadataJson.encodeToByteArray())
            }
        }

        suspend fun withEncryptedBackup(extractedBackupRootPath: Path, userId: UserId, password: String) = apply {
            with(fakeFileSystem) {
                createDirectory(extractedBackupRootPath)
                val encryptedBackupDataPath = encryptData(extractedBackupRootPath, fakeDBData, userId, password)
                sink(extractedBackupRootPath / encryptedBackupName).buffer().use {
                    it.writeAll(source(encryptedBackupDataPath))
                }
            }
        }

        fun withCorrectDbImportAction() = apply {
            given(databaseImporter)
                .suspendFunction(databaseImporter::importFromFile)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withIncorrectDbImportAction() = apply {
            given(databaseImporter)
                .suspendFunction(databaseImporter::importFromFile)
                .whenInvokedWith(any(), any())
                .thenThrow(RuntimeException("DB import failed"))
        }

        fun arrange() = this to RestoreBackupUseCaseImpl(
            databaseImporter = databaseImporter,
            kaliumFileSystem = fakeFileSystem,
            userId = selfUserId,
            idMapper = idMapper
        )
    }

    companion object {
        var fakeFileSystem = FakeKaliumFileSystem()
        val currentTestUserId = UserId("some-user-id", "some-domain")
    }
}
