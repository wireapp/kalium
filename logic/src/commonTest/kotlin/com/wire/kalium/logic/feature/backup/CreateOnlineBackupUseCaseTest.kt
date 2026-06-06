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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.backup.OnlineBackupMetadata
import com.wire.kalium.logic.data.backup.OnlineBackupRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class CreateOnlineBackupUseCaseTest {

    @Test
    fun givenNoRemoteBackupsAndLatestMessageExists_whenCreatingOnlineBackup_thenCreatesUploadsAndRegistersBackup() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemoteBackups(emptyList())
            .withLatestReceivedMessageDate(LATEST_MESSAGE_DATE)
            .arrange()

        val result = useCase {}

        assertIs<CreateOnlineBackupResult.Success>(result)
        assertEquals("self-user_${LATEST_MESSAGE_DATE.toEpochMilliseconds()}.wbu", arrangement.uploader.uploadedFileName)
        assertEquals(LATEST_MESSAGE_DATE, result.metadata.lastMessageDate)
        assertEquals("backup-id", result.metadata.backupId)
        assertEquals("root-key-id", result.metadata.rootKeyId)
        assertEquals("client-id", result.metadata.clientId)
    }

    @Test
    fun givenRemoteBackupOlderThanLatestMessage_whenCreatingOnlineBackup_thenCreatesBackup() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemoteBackups(listOf(onlineBackupMetadata(lastMessageDate = OLDER_BACKUP_DATE)))
            .withLatestReceivedMessageDate(LATEST_MESSAGE_DATE)
            .arrange()

        val result = useCase {}

        assertIs<CreateOnlineBackupResult.Success>(result)
        assertEquals("self-user_${LATEST_MESSAGE_DATE.toEpochMilliseconds()}.wbu", arrangement.uploader.uploadedFileName)
    }

    @Test
    fun givenRemoteBackupIsUpToDate_whenCreatingOnlineBackup_thenSkips() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemoteBackups(listOf(onlineBackupMetadata(lastMessageDate = LATEST_MESSAGE_DATE)))
            .withLatestReceivedMessageDate(LATEST_MESSAGE_DATE)
            .arrange()

        val result = useCase {}

        assertIs<CreateOnlineBackupResult.Skipped.UpToDate>(result)
        assertFalse(arrangement.createBackupFromRootKey.wasCalled)
        assertFalse(arrangement.uploader.wasCalled)
    }

    @Test
    fun givenNoReceivedMessages_whenCreatingOnlineBackup_thenSkips() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withRemoteBackups(emptyList())
            .withLatestReceivedMessageDate(null)
            .arrange()

        val result = useCase {}

        assertIs<CreateOnlineBackupResult.Skipped.NoReceivedMessages>(result)
        assertFalse(arrangement.createBackupFromRootKey.wasCalled)
        assertFalse(arrangement.uploader.wasCalled)
    }

    @Test
    fun givenBackupListFails_whenCreatingOnlineBackup_thenReturnsBackupListFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, useCase) = Arrangement()
            .withListBackupsResult(Either.Left(failure))
            .arrange()

        val result = useCase {}

        assertEquals(CreateOnlineBackupResult.Failure.BackupListFailed(failure), result)
    }

    @Test
    fun givenMessageTimestampFails_whenCreatingOnlineBackup_thenReturnsTimestampFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withRemoteBackups(emptyList())
            .withLatestReceivedMessageDateResult(Either.Left(failure))
            .arrange()

        val result = useCase {}

        assertEquals(CreateOnlineBackupResult.Failure.MessageTimestampFailed(failure), result)
    }

    @Test
    fun givenBackupCreationFails_whenCreatingOnlineBackup_thenReturnsBackupCreationFailure() = runTest {
        val failure = CreateBackupFromRootKeyResult.Failure.BackupCreationFailed(CoreFailure.Unknown(null))
        val (_, useCase) = Arrangement()
            .withRemoteBackups(emptyList())
            .withLatestReceivedMessageDate(LATEST_MESSAGE_DATE)
            .withCreateBackupResult(failure)
            .arrange()

        val result = useCase {}

        assertEquals(CreateOnlineBackupResult.Failure.BackupCreationFailed(failure), result)
    }

    @Test
    fun givenUploadFails_whenCreatingOnlineBackup_thenReturnsUploadFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, useCase) = Arrangement()
            .withRemoteBackups(emptyList())
            .withLatestReceivedMessageDate(LATEST_MESSAGE_DATE)
            .withUploadResult(Either.Left(failure))
            .arrange()

        val result = useCase {}

        assertEquals(CreateOnlineBackupResult.Failure.UploadFailed(failure), result)
    }

    @Test
    fun givenMetadataRegistrationFails_whenCreatingOnlineBackup_thenReturnsMetadataFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, useCase) = Arrangement()
            .withRemoteBackups(emptyList())
            .withLatestReceivedMessageDate(LATEST_MESSAGE_DATE)
            .withRegisterBackupResult(Either.Left(failure))
            .arrange()

        val result = useCase {}

        assertEquals(CreateOnlineBackupResult.Failure.MetadataRegistrationFailed(failure), result)
    }

    private class Arrangement {
        val messageRepository = mock<MessageRepository>()
        val onlineBackupRepository = RecordingOnlineBackupRepository()
        val createBackupFromRootKey = RecordingCreateBackupFromRootKeyUseCase()
        val uploader = RecordingBackupFileUploader()
        private var clientIdResult: Either<CoreFailure, ClientId> = ClientId("client-id").right()

        suspend fun withRemoteBackups(backups: List<OnlineBackupMetadata>) = apply {
            onlineBackupRepository.listResult = Either.Right(backups)
        }

        suspend fun withListBackupsResult(result: Either<CoreFailure, List<OnlineBackupMetadata>>) = apply {
            onlineBackupRepository.listResult = result
        }

        suspend fun withLatestReceivedMessageDate(date: Instant?) = withLatestReceivedMessageDateResult(Either.Right(date))

        suspend fun withLatestReceivedMessageDateResult(result: Either<StorageFailure, Instant?>) = apply {
            everySuspend { messageRepository.getLatestReceivedMessageDate() } returns result
        }

        fun withCreateBackupResult(result: CreateBackupFromRootKeyResult) = apply {
            createBackupFromRootKey.result = result
        }

        fun withUploadResult(result: Either<CoreFailure, UploadedAssetId>) = apply {
            uploader.result = result
        }

        fun withRegisterBackupResult(result: Either<CoreFailure, OnlineBackupMetadata>) = apply {
            onlineBackupRepository.registerResult = result
        }

        fun arrange(): Pair<Arrangement, CreateOnlineBackupUseCase> =
            this to CreateOnlineBackupUseCaseImpl(
                selfUserId = SELF_USER_ID,
                clientIdProvider = CurrentClientIdProvider { clientIdResult },
                onlineBackupRepository = onlineBackupRepository,
                messageRepository = messageRepository,
                createBackupFromRootKey = createBackupFromRootKey,
                backupFileUploader = uploader,
            )
    }

    private class RecordingOnlineBackupRepository : OnlineBackupRepository {
        var listResult: Either<CoreFailure, List<OnlineBackupMetadata>> = Either.Right(emptyList())
        var registerResult: Either<CoreFailure, OnlineBackupMetadata>? = null

        override suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>> = listResult

        override suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata> =
            registerResult ?: Either.Right(metadata)
    }

    private class RecordingCreateBackupFromRootKeyUseCase : CreateBackupFromRootKeyUseCase {
        var wasCalled = false
        var result: CreateBackupFromRootKeyResult = CreateBackupFromRootKeyResult.Success(
            backupFilePath = "local-backup.wbu".toPath(),
            backupFileName = "local-backup.wbu",
            backupId = "backup-id",
            rootKeyId = "root-key-id",
            encryptionAlgorithm = "backup-root-key-hkdf-v1",
        )

        override suspend fun invoke(onProgress: (Float) -> Unit): CreateBackupFromRootKeyResult {
            wasCalled = true
            return result
        }
    }

    private class RecordingBackupFileUploader : BackupFileUploader {
        var wasCalled = false
        var uploadedFileName: String? = null
        var result: Either<CoreFailure, UploadedAssetId> = Either.Right(UPLOADED_ASSET_ID)

        override suspend fun upload(filePath: okio.Path, fileName: String): Either<CoreFailure, UploadedAssetId> {
            wasCalled = true
            uploadedFileName = fileName
            return result
        }
    }

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.com")
        val LATEST_MESSAGE_DATE = Instant.parse("2026-01-02T03:04:05Z")
        val OLDER_BACKUP_DATE = Instant.parse("2026-01-01T03:04:05Z")
        val UPLOADED_ASSET_ID = UploadedAssetId("asset-id", "asset-domain", "asset-token")

        fun onlineBackupMetadata(lastMessageDate: Instant): OnlineBackupMetadata = OnlineBackupMetadata(
            backupId = "existing-backup-id",
            userId = SELF_USER_ID,
            clientId = "client-id",
            fileName = "existing.wbu",
            lastMessageDate = lastMessageDate,
            assetId = UPLOADED_ASSET_ID,
            rootKeyId = "root-key-id",
            encryptionAlgorithm = "backup-root-key-hkdf-v1",
        )
    }
}
