package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_ENCRYPTED_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_METADATA_FILE_NAME
import com.wire.kalium.logic.feature.client.ObserveCurrentClientIdUseCase
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.persistence.db.UserDBSecret
import io.ktor.util.decodeBase64Bytes
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateBackupUseCaseTest {

    private val fakeFileSystem = FakeKaliumFileSystem()
    private val dispatcher = TestKaliumDispatcher

    @Test
    fun givenSomeValidData_whenCreatingNonEncryptedBackup_thenTheFinalBackupFileIsCreatedCorrectly() = runTest(dispatcher.default) {
        // Given
        val password = ""
        val currentDB = "some-dummy.db".decodeBase64Bytes()
        val (arrangement, createBackupUseCase) = Arrangement()
            .withObservedClientId(ClientId("client-id"))
            .withProvidedDB(currentDB)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertTrue(result is CreateBackupResult.Success)
        assertEquals(result.backupFilePath.name, BACKUP_FILE_NAME)
        verify(arrangement.observeClientId)
            .suspendFunction(arrangement.observeClientId::invoke)
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
            .withObservedClientId(ClientId("client-id"))
            .withProvidedDB(dummyDB)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertTrue(result is CreateBackupResult.Failure)
        assertTrue(result.coreFailure is StorageFailure.DataNotFound)
        verify(arrangement.observeClientId)
            .suspendFunction(arrangement.observeClientId::invoke)
            .wasInvoked(once)
    }

    @Test
    fun givenSomeValidData_whenCreatingAnEncryptedBackup_thenTheFinalBackupFileIsCreatedCorrectly() = runTest(dispatcher.default) {
        // Given
        val password = "S0m3T0pS3CR3tP4\$\$w0rd"
        val dummyDB = "some-dummy.db".decodeBase64Bytes()
        val (arrangement, createBackupUseCase) = Arrangement()
            .withObservedClientId(ClientId("client-id"))
            .withProvidedDB(dummyDB)
            .arrange()

        // When
        val result = createBackupUseCase(password)
        advanceUntilIdle()

        // Then
        assertTrue(result is CreateBackupResult.Success)
        verify(arrangement.observeClientId)
            .suspendFunction(arrangement.observeClientId::invoke)
            .wasInvoked(once)

        // Check there is only one .cc20 file in the backup file
        with(fakeFileSystem) {
            val extractedFilesPath = tempFilePath()
            createDirectory(extractedFilesPath)
            extractCompressedFile(source(result.backupFilePath), extractedFilesPath, fakeFileSystem)

            assertTrue(listDirectories(extractedFilesPath).firstOrNull { it.name == BACKUP_ENCRYPTED_FILE_NAME } != null)
        }
    }

    @Suppress("NestedBlockDepth")
    private inner class Arrangement {
        private var userId = UserId("some-user-id", "some-user-domain")
        private var userDBSecret = UserDBSecret("some-user-db-secret".decodeBase64Bytes())
        private var isUserDBSQLCiphered = false

        @Mock
        val observeClientId = mock(classOf<ObserveCurrentClientIdUseCase>())

        fun withObservedClientId(clientId: ClientId?) = apply {
            given(observeClientId)
                .suspendFunction(observeClientId::invoke)
                .whenInvoked()
                .thenReturn(flowOf(clientId))
        }

        fun withProvidedDB(dbData: ByteArray?) = apply {
            with(fakeFileSystem) {
                dbData?.let { rawData ->
                    sink(rootDBPath).buffer().use { it.write(rawData) }
                }
            }
        }

        fun arrange(): Pair<Arrangement, CreateBackupUseCase> =
            this to CreateBackupUseCaseImpl(userId, observeClientId, fakeFileSystem, userDBSecret, isUserDBSQLCiphered, dispatcher)

    }

}
