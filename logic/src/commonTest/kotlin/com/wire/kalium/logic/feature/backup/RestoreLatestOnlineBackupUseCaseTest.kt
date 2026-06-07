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
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.backup.OnlineBackupMetadata
import com.wire.kalium.logic.data.backup.OnlineBackupRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RestoreLatestOnlineBackupUseCaseTest {

    @Test
    fun givenLocalRootKeyAndOnlineBackup_whenRestoring_thenSuccess() = runTest {
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(listOf(METADATA))
            .arrange()

        val result = useCase {}

        assertIs<RestoreLatestOnlineBackupResult.Success>(result)
        assertEquals(METADATA.fileName, result.metadata.fileName)
    }

    @Test
    fun givenNoLocalKeyButSyncFindsKey_whenRestoring_thenSuccess() = runTest {
        val (_, useCase) = Arrangement()
            .withLocalRootKey(null)
            .withSyncResult(SyncBackupRootKeyResult.Found(ROOT_KEY))
            .withRemoteBackups(listOf(METADATA))
            .arrange()

        val result = useCase {}

        assertIs<RestoreLatestOnlineBackupResult.Success>(result)
    }

    @Test
    fun givenNoLocalKeyAndSyncUnavailable_whenRestoring_thenNoBackupRootKeyAvailable() = runTest {
        val (_, useCase) = Arrangement()
            .withLocalRootKey(null)
            .withSyncResult(SyncBackupRootKeyResult.Unavailable)
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.NoBackupRootKeyAvailable, result)
    }

    @Test
    fun givenNoLocalKeyAndSyncFailure_whenRestoring_thenNoBackupRootKeyAvailable() = runTest {
        val (_, useCase) = Arrangement()
            .withLocalRootKey(null)
            .withSyncResult(SyncBackupRootKeyResult.Failure(IllegalStateException("boom")))
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.NoBackupRootKeyAvailable, result)
    }

    @Test
    fun givenEmptyRemoteBackups_whenRestoring_thenNoOnlineBackupFound() = runTest {
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(emptyList())
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.NoOnlineBackupFound, result)
    }

    @Test
    fun givenListBackupsFails_whenRestoring_thenBackupListFailed() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withListBackupsResult(Either.Left(failure))
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.BackupListFailed(failure), result)
    }

    @Test
    fun givenBackupForOtherUser_whenRestoring_thenBackupBelongsToAnotherUser() = runTest {
        val foreignMetadata = METADATA.copy(userId = UserId("other", "wire.com"))
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(listOf(foreignMetadata))
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.BackupBelongsToAnotherUser, result)
    }

    @Test
    fun givenRootKeyIdMismatch_whenRestoring_thenRootKeyIdMismatch() = runTest {
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(listOf(METADATA.copy(rootKeyId = "different-root-key-id")))
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.RootKeyIdMismatch, result)
    }

    @Test
    fun givenDownloadFails_whenRestoring_thenDownloadFailed() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(listOf(METADATA))
            .withDownloadResult(Either.Left(failure))
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.DownloadFailed(failure), result)
    }

    @Test
    fun givenInvalidPassphrase_whenRestoring_thenInvalidPassphrase() = runTest {
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(listOf(METADATA))
            .withRestoreResult(RestoreBackupResult.Failure(RestoreBackupResult.BackupRestoreFailure.InvalidPassword))
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.InvalidPassphrase, result)
    }

    @Test
    fun givenImportFailure_whenRestoring_thenRestoreFailed() = runTest {
        val failure = RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("io")
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(listOf(METADATA))
            .withRestoreResult(RestoreBackupResult.Failure(failure))
            .arrange()

        val result = useCase {}

        assertEquals(RestoreLatestOnlineBackupResult.Failure.RestoreFailed(failure), result)
    }

    @Test
    fun givenMultipleBackups_whenRestoring_thenLatestByLastMessageDateIsPicked() = runTest {
        val older = METADATA.copy(
            backupId = "older",
            fileName = "older.wbu",
            lastMessageDate = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val newer = METADATA.copy(
            backupId = "newer",
            fileName = "newer.wbu",
            lastMessageDate = Instant.parse("2026-02-01T00:00:00Z"),
        )
        val (_, useCase) = Arrangement()
            .withLocalRootKey(ROOT_KEY)
            .withRemoteBackups(listOf(older, newer))
            .arrange()

        val result = useCase {}

        assertIs<RestoreLatestOnlineBackupResult.Success>(result)
        assertEquals("newer.wbu", result.metadata.fileName)
    }

    private class Arrangement {
        val rootKeyRepository = RecordingBackupRootKeyRepository()
        val onlineBackupRepository = RecordingOnlineBackupRepository()
        val restore = RecordingRestoreMPBackupUseCase()
        val kaliumFileSystem = mock<KaliumFileSystem>()
        private var syncResult: SyncBackupRootKeyResult = SyncBackupRootKeyResult.Unavailable

        fun withLocalRootKey(key: BackupRootKey?) = apply {
            rootKeyRepository.values = listOf(key)
        }

        fun withSyncResult(result: SyncBackupRootKeyResult) = apply {
            syncResult = result
        }

        fun withRemoteBackups(backups: List<OnlineBackupMetadata>) = apply {
            onlineBackupRepository.listResult = Either.Right(backups)
        }

        fun withListBackupsResult(result: Either<CoreFailure, List<OnlineBackupMetadata>>) = apply {
            onlineBackupRepository.listResult = result
        }

        fun withDownloadResult(result: Either<CoreFailure, Path>) = apply {
            onlineBackupRepository.downloadResult = result
        }

        fun withRestoreResult(result: RestoreBackupResult) = apply {
            restore.result = result
        }

        fun arrange(): Pair<Arrangement, RestoreLatestOnlineBackupUseCase> {
            everySuspend { kaliumFileSystem.delete(any(), any()) } returns Unit
            return this to RestoreLatestOnlineBackupUseCaseImpl(
                selfUserId = SELF_USER_ID,
                backupRootKeyRepository = rootKeyRepository,
                syncBackupRootKey = object : SyncBackupRootKeyUseCase {
                    override suspend fun invoke(): SyncBackupRootKeyResult = syncResult
                },
                onlineBackupRepository = onlineBackupRepository,
                backupEncryptionKeyDeriver = HkdfBackupEncryptionKeyDeriver,
                restoreMPBackup = restore,
                kaliumFileSystem = kaliumFileSystem,
            )
        }
    }

    private class RecordingBackupRootKeyRepository : BackupRootKeyRepository {
        var values: List<BackupRootKey?> = listOf(null)
        private var index = 0

        override suspend fun getBackupRootKey(): BackupRootKey? {
            val value = values.getOrElse(index) { values.lastOrNull() }
            index++
            return value
        }

        override suspend fun setBackupRootKey(backupRootKey: BackupRootKey) = Unit

        override suspend fun clearBackupRootKey() = Unit
    }

    private class RecordingOnlineBackupRepository : OnlineBackupRepository {
        var listResult: Either<CoreFailure, List<OnlineBackupMetadata>> = Either.Right(emptyList())
        var downloadResult: Either<CoreFailure, Path> = Either.Right("downloaded.wbu".toPath())

        override suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>> = listResult

        override suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata> =
            Either.Right(metadata)

        override suspend fun downloadBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, Path> = downloadResult
    }

    private class RecordingRestoreMPBackupUseCase : RestoreMPBackupUseCase {
        var result: RestoreBackupResult = RestoreBackupResult.Success
        override suspend fun invoke(backupFilePath: Path, password: String?, onProgress: (Float) -> Unit): RestoreBackupResult = result
    }

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.com")
        val ROOT_KEY = BackupRootKey(
            id = "root-key-id",
            keyMaterial = ByteArray(32) { it.toByte() },
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdByClientId = ClientId("creator-client"),
            version = 1,
        )
        val UPLOADED_ASSET_ID = UploadedAssetId("asset-id", "asset-domain", "remote/path.wbu")
        val METADATA = OnlineBackupMetadata(
            backupId = "backup-id",
            userId = SELF_USER_ID,
            clientId = "client-id",
            fileName = "self-user_123.wbu",
            lastMessageDate = Instant.parse("2026-01-02T03:04:05Z"),
            assetId = UPLOADED_ASSET_ID,
            rootKeyId = "root-key-id",
            encryptionAlgorithm = "backup-root-key-hkdf-v1",
        )
    }
}
