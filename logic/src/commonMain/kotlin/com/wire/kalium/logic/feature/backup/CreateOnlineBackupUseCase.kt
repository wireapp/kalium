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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.backup.OnlineBackupMetadata
import com.wire.kalium.logic.data.backup.OnlineBackupRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant

/**
 * Creates and uploads an online backup when local messages are newer than the latest remote backup.
 */
public interface CreateOnlineBackupUseCase {
    public suspend operator fun invoke(onProgress: (Float) -> Unit): CreateOnlineBackupResult
}

public sealed interface CreateOnlineBackupResult {
    public data class Success(val metadata: OnlineBackupMetadata) : CreateOnlineBackupResult

    public sealed interface Skipped : CreateOnlineBackupResult {
        public data object NoReceivedMessages : Skipped
        public data class UpToDate(
            val latestBackupTimestamp: Instant,
            val latestMessageTimestamp: Instant,
        ) : Skipped
    }

    public sealed interface Failure : CreateOnlineBackupResult {
        public data class BackupListFailed(val cause: CoreFailure) : Failure
        public data class MessageTimestampFailed(val cause: CoreFailure) : Failure
        public data class BackupCreationFailed(val cause: CreateBackupFromRootKeyResult.Failure) : Failure
        public data class UploadFailed(val cause: CoreFailure) : Failure
        public data class MetadataRegistrationFailed(val cause: CoreFailure) : Failure
        public data class Unknown(val cause: Throwable) : Failure
    }
}

@Suppress("LongParameterList")
internal class CreateOnlineBackupUseCaseImpl(
    private val selfUserId: UserId,
    private val clientIdProvider: CurrentClientIdProvider,
    private val onlineBackupRepository: OnlineBackupRepository,
    private val messageRepository: MessageRepository,
    private val createBackupFromRootKey: CreateBackupFromRootKeyUseCase,
    private val backupFileUploader: BackupFileUploader,
) : CreateOnlineBackupUseCase {

    override suspend fun invoke(onProgress: (Float) -> Unit): CreateOnlineBackupResult =
        try {
            val latestOnlineBackupTimestamp = when (val result = onlineBackupRepository.listBackups()) {
                is Either.Left -> return CreateOnlineBackupResult.Failure.BackupListFailed(result.value)
                is Either.Right -> result.value
                    .maxByOrNull { it.lastMessageDate.toEpochMilliseconds() }
                    ?.lastMessageDate
            }

            val latestMessageTimestamp = when (val result = messageRepository.getLatestReceivedMessageDate()) {
                is Either.Left -> return CreateOnlineBackupResult.Failure.MessageTimestampFailed(result.value)
                is Either.Right -> result.value ?: return CreateOnlineBackupResult.Skipped.NoReceivedMessages
            }

            if (
                latestOnlineBackupTimestamp != null &&
                latestOnlineBackupTimestamp.toEpochMilliseconds() >= latestMessageTimestamp.toEpochMilliseconds()
            ) {
                return CreateOnlineBackupResult.Skipped.UpToDate(
                    latestBackupTimestamp = latestOnlineBackupTimestamp,
                    latestMessageTimestamp = latestMessageTimestamp,
                )
            }

            val createdBackup = when (val result = createBackupFromRootKey(onProgress)) {
                is CreateBackupFromRootKeyResult.Success -> result
                is CreateBackupFromRootKeyResult.Failure ->
                    return CreateOnlineBackupResult.Failure.BackupCreationFailed(result)
            }

            val fileName = createOnlineBackupFileName(selfUserId, latestMessageTimestamp)
            val uploadedAssetId = backupFileUploader.upload(createdBackup.backupFilePath, fileName).fold(
                { return CreateOnlineBackupResult.Failure.UploadFailed(it) },
                { it }
            )
            val clientId = clientIdProvider().fold(
                { return CreateOnlineBackupResult.Failure.MetadataRegistrationFailed(it) },
                { it.value }
            )

            val metadata = OnlineBackupMetadata(
                backupId = createdBackup.backupId,
                userId = selfUserId,
                clientId = clientId,
                fileName = fileName,
                lastMessageDate = latestMessageTimestamp,
                assetId = uploadedAssetId,
                rootKeyId = createdBackup.rootKeyId,
                encryptionAlgorithm = createdBackup.encryptionAlgorithm,
            )

            onlineBackupRepository.registerBackup(metadata).fold(
                { CreateOnlineBackupResult.Failure.MetadataRegistrationFailed(it) },
                { CreateOnlineBackupResult.Success(it) }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CreateOnlineBackupResult.Failure.Unknown(e)
        }

    private fun createOnlineBackupFileName(selfUserId: UserId, lastMessageDate: Instant): String =
        "${selfUserId.value}_${lastMessageDate.toEpochMilliseconds()}.wbu"
}
