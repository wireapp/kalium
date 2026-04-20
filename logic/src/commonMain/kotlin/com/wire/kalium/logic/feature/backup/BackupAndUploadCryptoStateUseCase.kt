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
@file:Suppress("TooGenericExceptionCaught")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.nomadTrace
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.CryptoStateBackupRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.blackholeSink
import okio.buffer
import okio.use

/**
 * Creates a crypto state backup and uploads it to the remote endpoint.
 */
public interface BackupAndUploadCryptoStateUseCase {
    /**
     * Creates and uploads the crypto state backup.
     */
    public suspend operator fun invoke(): BackupAndUploadCryptoStateResult
}

internal class BackupAndUploadCryptoStateUseCaseImpl(
    private val backupCryptoDBUseCase: BackupCryptoDBUseCase,
    private val cryptoStateBackupRemoteRepository: CryptoStateBackupRemoteRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val currentClientIdProvider: CurrentClientIdProvider,
) : BackupAndUploadCryptoStateUseCase {
    /** Mutex to ensure only one backup/upload operation runs at a time. All shared state access must be within [mutex.withLock]. */
    private val mutex = Mutex()

    /** Tracks whether a backup/upload operation is currently running. Protected by [mutex]. */
    private var isInFlight = false

    /**
     * Flag indicating that one or more requests were coalesced during the current upload.
     * When true, a trailing upload will execute after the current one finishes to capture the latest state.
     * Protected by [mutex].
     */
    private var hasPendingRun = false

    /**
     * Creates and uploads the crypto state backup with coalescing to ensure sequential upload ordering.
     *
     * **Coalescing Behavior:**
     * - If an upload is already in-flight when this is called, the request is coalesced:
     *   - Sets a "pending run" flag to trigger one trailing upload after the current one finishes
     *   - Returns [BackupAndUploadCryptoStateResult.Success] immediately without performing work
     *   - The in-flight upload operation will capture the latest state in its trailing run
     *
     * **Sequential Upload Guarantee:**
     * - Only one upload operation runs at a time (enforced by [mutex])
     * - If requests are coalesced during an upload, exactly ONE trailing upload executes afterward
     * - The trailing upload captures the most recent crypto state and event anchor
     * - This prevents out-of-order uploads (older backup uploaded after newer one)
     *
     * **Failure Handling:**
     * - If backup creation or upload fails, the trailing run still executes (doesn't skip pending requests)
     * - Each iteration returns its own result; coalesced callers receive [Success]
     * - The [finally] block ensures cleanup ([isInFlight], [hasPendingRun]) even on exceptions
     *
     * @return [BackupAndUploadCryptoStateResult.Success] if backup created and uploaded (or coalesced),
     *         [BackupAndUploadCryptoStateResult.Failure] if backup creation or upload failed
     */
    override suspend fun invoke(): BackupAndUploadCryptoStateResult {
        var wasCoalesced = false // track if we coalesced this run with an in-flight operation
        mutex.withLock {
            if (isInFlight) {
                hasPendingRun = true
                wasCoalesced = true
                return@withLock
            }
            isInFlight = true
        }
        if (wasCoalesced) { // if we coalesced, return success without doing work, since the in-flight operation will cover our request
            return BackupAndUploadCryptoStateResult.Success
        }

        var lastResult: BackupAndUploadCryptoStateResult
        try {
            while (true) {
                // run the backup/upload operation at least once, and repeat if we had coalesced runs while it was in-flight
                lastResult = runOnce()
                val shouldRepeat = mutex.withLock {
                    if (hasPendingRun) { // any requests while uploading
                        hasPendingRun = false // clear and run again
                        true
                    } else {
                        false
                    }
                }
                if (!shouldRepeat) {
                    return lastResult
                }
            }
        } finally {
            mutex.withLock {
                isInFlight = false
                hasPendingRun = false
            }
        }
    }

    private suspend fun runOnce(): BackupAndUploadCryptoStateResult =
        when (val backupResult = backupCryptoDBUseCase.invoke()) {
            is BackupCryptoDBResult.Success -> uploadBackup(backupResult)

            is BackupCryptoDBResult.Failure -> {
                kaliumLogger.e("Failed to create crypto state backup ${backupResult.error}")
                kaliumLogger.nomadTrace(
                    stage = "backup.create.failure",
                    fields = mapOf("error" to backupResult.error)
                )
                BackupAndUploadCryptoStateResult.Failure(backupResult.error)
            }
        }

    private suspend fun uploadBackup(backupResult: BackupCryptoDBResult.Success): BackupAndUploadCryptoStateResult {
        val clientId = when (val clientResult = currentClientIdProvider.invoke()) {
            is Either.Left -> {
                kaliumLogger.e("Failed to read current client id")
                kaliumLogger.nomadTrace(
                    stage = "backup.upload.client_id.failure",
                    fields = mapOf("error" to clientResult.value)
                )
                return BackupAndUploadCryptoStateResult.Failure(clientResult.value)
            }

            is Either.Right -> clientResult.value
        }
        val backupSize = kaliumFileSystem.source(backupResult.backupFilePath).use { source ->
            source.buffer().readAll(blackholeSink())
        }
        kaliumLogger.nomadTrace(
            stage = "backup.upload.start",
            fields = mapOf(
                "clientId" to clientId.value.obfuscateId(),
                "backupPath" to backupResult.backupFilePath.toString(),
                "backupSizeBytes" to backupSize,
                "lastProcessedEventId" to backupResult.lastProcessedEventId
            )
        )
        val uploadResult = cryptoStateBackupRemoteRepository.uploadCryptoState(
            clientId = clientId.value,
            sourceProvider = { kaliumFileSystem.source(backupResult.backupFilePath) },
            size = backupSize
        ).mapLeft { error ->
            kaliumLogger.e("Failed to upload crypto state backup")
            kaliumLogger.nomadTrace(
                stage = "backup.upload.failure",
                fields = mapOf(
                    "clientId" to clientId.value.obfuscateId(),
                    "lastProcessedEventId" to backupResult.lastProcessedEventId,
                    "error" to error
                )
            )
            error
        }
        return when (uploadResult) {
            is Either.Left ->
                BackupAndUploadCryptoStateResult.Failure(uploadResult.value)

            is Either.Right -> {
                kaliumLogger.nomadTrace(
                    stage = "backup.upload.success",
                    fields = mapOf(
                        "clientId" to clientId.value.obfuscateId(),
                        "lastProcessedEventId" to backupResult.lastProcessedEventId,
                        "backupSizeBytes" to backupSize
                    )
                )
                BackupAndUploadCryptoStateResult.Success
            }
        }
    }
}

public sealed class BackupAndUploadCryptoStateResult {
    public data object Success : BackupAndUploadCryptoStateResult()
    public data class Failure(public val error: CoreFailure) : BackupAndUploadCryptoStateResult()
}
