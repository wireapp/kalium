package com.wire.kalium.logic.feature.backup

import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.utils.ChaCha20Encryptor.encryptBackupFile
import com.wire.kalium.logic.clientPlatform
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.persistence.backup.DatabaseImporter
import com.wire.kalium.persistence.db.UserDBSecret
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreBackupUseCaseTest {

    private lateinit var fakeFileSystem: FakeKaliumFileSystem

    @BeforeTest
    fun setup() {
        fakeFileSystem =  FakeKaliumFileSystem()
    }

    @Test
    fun givenACorrectNonEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredSuccessfully() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.zip")
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(backupPath, currentTestUserId)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Success)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), eq(null))
            .wasInvoked(once)
    }

    @Test
    fun givenACorrectNonEncryptedBackupFileWithWrongAuthor_whenRestoring_thenTheCorrectErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.zip")
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(backupPath, UserId("wrongUserId", "wrongDomain"))
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), eq(null))
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectNonEncryptedBackupFileWithWrongMetadataFileName_whenRestoring_thenTheCorrectErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.zip")
        val (arrangement, useCase) = Arrangement()
            .withUnencryptedBackup(backupPath, currentTestUserId, true)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, "")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), eq(null))
            .wasNotInvoked()
    }

    @Test
    fun givenAValidEncryptedBackupFile_whenRestoring_thenTheBackupIsRestoredCorrectly() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(backupPath, currentTestUserId, password)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Success)

        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), eq(null))
            .wasInvoked(once)
    }

    @Test
    fun givenAnEncryptedBackupFileFromDifferentUserID_whenRestoring_thenTheRightErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(backupPath, UserId("Darth-Vader", "death-star"), password)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidUserId)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), eq(null))
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectlyEncryptedBackup_whenRestoringWithWrongPassword_thenTheRightErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(backupPath, currentTestUserId, password)
            .withCorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, "OhIForgotMyPassword")

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.InvalidPassword)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), eq(null))
            .wasNotInvoked()
    }

    @Test
    fun givenACorrectlyEncryptedBackup_whenRestoringWithADBImportError_thenTheRightErrorIsThrown() = runTest {
        // given
        val backupPath = fakeFileSystem.tempFilePath("backup.cc20.zip")
        val password = "KittenWars"
        val (arrangement, useCase) = Arrangement()
            .withEncryptedBackup(backupPath, currentTestUserId, password)
            .withIncorrectDbImportAction()
            .arrange()

        // when
        val result = useCase(backupPath, password)

        // then
        assertTrue(result is RestoreBackupResult.Failure)
        assertTrue(result.failure is RestoreBackupResult.BackupRestoreFailure.BackupIOFailure)
        verify(arrangement.databaseImporter)
            .suspendFunction(arrangement.databaseImporter::importFromFile)
            .with(any(), eq(null))
            .wasInvoked(once)
    }

    private inner class Arrangement {

        @Mock
        val databaseImporter = mock(classOf<DatabaseImporter>())

        val fakeDBFileName = "fakeDBFile.db"
        private val selfUserId = currentTestUserId
        private val fakeDBData = fakeDBFileName.encodeToByteArray()
        private val idMapper = MapperProvider.idMapper()

        private fun createMetadataFile(metadataFilePath: Path, userId: UserId, userDBSecret: UserDBSecret? = null): Path {
            val clientId = "dummy-client-id"
            val creationTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
            val metadataJson =
                BackupMetadata(
                    clientPlatform,
                    BackupCoder.version,
                    userId.toString(),
                    creationTime,
                    clientId,
                    userDBSecret?.value?.encodeBase64(),
                    true
                ).toString()
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
                .whenInvokedWith(any(), eq(userDBSecret))
                .thenReturn(Unit)
        }

        fun withIncorrectDbImportAction(userDBSecret: UserDBSecret? = null) = apply {
            given(databaseImporter)
                .suspendFunction(databaseImporter::importFromFile)
                .whenInvokedWith(any(), eq(userDBSecret))
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
        val currentTestUserId = UserId("some-user-id", "some-domain")
    }
}
