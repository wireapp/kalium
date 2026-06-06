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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.OnlineBackupMetadata
import com.wire.kalium.logic.data.backup.OnlineBackupRepository
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CancellationException

/**
 * Restores the latest online backup uploaded by another self client.
 *
 * Flow:
 * 1. Get backup root key from local storage; if missing, sync from other self clients.
 * 2. List remote backups, filter for current user, pick the latest by `lastMessageDate`.
 * 3. Validate backup belongs to self and references the known root key id.
 * 4. Derive HKDF passphrase from the root key and backup id.
 * 5. Download backup file, decrypt, and import via [RestoreMPBackupUseCase].
 */
public interface RestoreLatestOnlineBackupUseCase {
    public suspend operator fun invoke(onProgress: (Float) -> Unit): RestoreLatestOnlineBackupResult
}

public sealed interface RestoreLatestOnlineBackupResult {
    public data class Success(val metadata: OnlineBackupMetadata) : RestoreLatestOnlineBackupResult

    public sealed interface Failure : RestoreLatestOnlineBackupResult {
        public data object NoBackupRootKeyAvailable : Failure
        public data object NoOnlineBackupFound : Failure
        public data object RootKeyIdMismatch : Failure
        public data object BackupBelongsToAnotherUser : Failure
        public data class BackupListFailed(val cause: CoreFailure) : Failure
        public data class DownloadFailed(val cause: CoreFailure) : Failure
        public data object InvalidPassphrase : Failure
        public data class RestoreFailed(val cause: RestoreBackupResult.BackupRestoreFailure) : Failure
        public data class Unknown(val cause: Throwable) : Failure
    }
}

@Suppress("LongParameterList", "ReturnCount")
internal class RestoreLatestOnlineBackupUseCaseImpl(
    private val selfUserId: UserId,
    private val backupRootKeyRepository: BackupRootKeyRepository,
    private val syncBackupRootKey: SyncBackupRootKeyUseCase,
    private val onlineBackupRepository: OnlineBackupRepository,
    private val backupEncryptionKeyDeriver: BackupEncryptionKeyDeriver,
    private val restoreMPBackup: RestoreMPBackupUseCase,
    private val kaliumFileSystem: KaliumFileSystem,
) : RestoreLatestOnlineBackupUseCase {

    override suspend fun invoke(onProgress: (Float) -> Unit): RestoreLatestOnlineBackupResult =
        try {
            val rootKey = resolveRootKey()
                ?: return RestoreLatestOnlineBackupResult.Failure.NoBackupRootKeyAvailable

            val latest = when (val result = onlineBackupRepository.listBackups()) {
                is Either.Left -> return RestoreLatestOnlineBackupResult.Failure.BackupListFailed(result.value)
                is Either.Right -> result.value.maxByOrNull { it.lastMessageDate.toEpochMilliseconds() }
            } ?: return RestoreLatestOnlineBackupResult.Failure.NoOnlineBackupFound

            if (latest.userId != selfUserId) {
                return RestoreLatestOnlineBackupResult.Failure.BackupBelongsToAnotherUser
            }
            if (latest.rootKeyId != rootKey.id) {
                return RestoreLatestOnlineBackupResult.Failure.RootKeyIdMismatch
            }

            val passphrase = backupEncryptionKeyDeriver.deriveBase64Passphrase(
                backupRootKey = rootKey.keyMaterial,
                backupId = latest.backupId,
            )

            val backupPath = when (val result = onlineBackupRepository.downloadBackup(latest)) {
                is Either.Left -> return RestoreLatestOnlineBackupResult.Failure.DownloadFailed(result.value)
                is Either.Right -> result.value
            }

            try {
                when (val result = restoreMPBackup(backupPath, passphrase, onProgress)) {
                    RestoreBackupResult.Success -> RestoreLatestOnlineBackupResult.Success(latest)
                    is RestoreBackupResult.Failure -> when (result.failure) {
                        RestoreBackupResult.BackupRestoreFailure.InvalidPassword ->
                            RestoreLatestOnlineBackupResult.Failure.InvalidPassphrase
                        else ->
                            RestoreLatestOnlineBackupResult.Failure.RestoreFailed(result.failure)
                    }
                }
            } finally {
                kaliumFileSystem.delete(backupPath, mustExist = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RestoreLatestOnlineBackupResult.Failure.Unknown(e)
        }

    private suspend fun resolveRootKey(): BackupRootKey? {
        backupRootKeyRepository.getBackupRootKey()?.let { return it }
        return when (val syncResult = syncBackupRootKey()) {
            is SyncBackupRootKeyResult.Found -> syncResult.backupRootKey
            SyncBackupRootKeyResult.LocalKeyExists -> backupRootKeyRepository.getBackupRootKey()
            SyncBackupRootKeyResult.Unavailable -> null
            is SyncBackupRootKeyResult.Failure -> null
        }
    }
}
