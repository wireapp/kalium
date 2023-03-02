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

import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.event.toMigratedMessage
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.web.WebContent
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_WEB_MESSAGES_FILE_NAME
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.BackupIOFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.incremental.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.decodeToSequence
import okio.Path
import okio.buffer
import okio.use

interface RestoreWebBackupUseCase {

    /**
     * Restores a valid previously created backup file into the current database, respecting the current data if there is any overlap.
     * @param backupRootPath The absolute file system path to the backup files.
     * @param metadata Containing information about backup files
     * @return A [RestoreBackupResult] indicating the success or failure of the operation.
     */
    suspend operator fun invoke(backupRootPath: Path, metadata: BackupMetadata): RestoreBackupResult
}

@Suppress("TooManyFunctions", "LongParameterList")
internal class RestoreWebBackupUseCaseImpl(
    private val kaliumFileSystem: KaliumFileSystem,
    private val userId: UserId,
    private val persistMigratedMessages: PersistMigratedMessagesUseCase,
    private val restartSlowSyncProcessForRecovery: RestartSlowSyncProcessForRecoveryUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : RestoreWebBackupUseCase {

    override suspend operator fun invoke(backupRootPath: Path, metadata: BackupMetadata): RestoreBackupResult =
        withContext(dispatchers.io) {
            // currently we will support only latest version for testing purposes
            if (metadata.version == "19") {
                importWebBackup(backupRootPath, this)
            } else {
                Either.Left(IncompatibleBackup("The provided backup format is not supported"))
            }.fold({ RestoreBackupResult.Failure(it) }, { RestoreBackupResult.Success })
        }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun importWebBackup(
        filePath: Path,
        coroutineScope: CoroutineScope
    ): Either<RestoreBackupResult.BackupRestoreFailure, Unit> = with(kaliumFileSystem) {
        listDirectories(filePath).firstOrNull { it.name == BACKUP_WEB_MESSAGES_FILE_NAME }?.let { path ->
            source(path).buffer()
                .use {
                    val sequence = KtxSerializer.json.decodeToSequence<WebContent>(
                        it.inputStream(),
                        DecodeSequenceMode.ARRAY_WRAPPED
                    )
                    val iterator = sequence.iterator()

                    val migratedMessagesBatch = mutableListOf<MigratedMessage>()
                    while (iterator.hasNext()) {
                        val webContent = iterator.next()
                        val migratedMessage = webContent.toMigratedMessage(userId.domain)
                        if (migratedMessage != null) {
                            migratedMessagesBatch.add(migratedMessage)
                        }

                        // send migrated messages in batches to not face any OOM errors
                        if (migratedMessagesBatch.size == 1000) {
                            persistMigratedMessages(migratedMessagesBatch, coroutineScope)
                            migratedMessagesBatch.clear()
                        }
                    }
                    persistMigratedMessages(migratedMessagesBatch, coroutineScope)
                    migratedMessagesBatch.clear()

                    kaliumLogger.d("$TAG restartSlowSyncProcessForRecovery")
                    restartSlowSyncProcessForRecovery.invoke()
                    Either.Right(Unit)
                }
        } ?: Either.Left(BackupIOFailure("No valid db file found in the backup"))
    }

    private companion object {
        const val TAG = "[RestoreWebBackupUseCase]"
    }
}
