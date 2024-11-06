/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
@file:Suppress("konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.backup

import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.web.WebConversationContent
import com.wire.kalium.logic.data.web.WebEventContent
import com.wire.kalium.logic.data.web.toConversation
import com.wire.kalium.logic.data.web.toMigratedMessage
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_WEB_CONVERSATIONS_FILE_NAME
import com.wire.kalium.logic.feature.backup.BackupConstants.BACKUP_WEB_EVENTS_FILE_NAME
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.BackupIOFailure
import com.wire.kalium.logic.feature.backup.RestoreBackupResult.BackupRestoreFailure.IncompatibleBackup
import com.wire.kalium.logic.feature.message.PersistMigratedMessagesUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.util.decodeBufferSequence
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.MigrationDAO
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import okio.Path
import okio.buffer
import okio.use

interface RestoreWebBackupUseCase {

    /**
     * Restores a valid previously created web backup file into the current database, respecting the current data if there is any overlap.
     * @param backupRootPath The absolute file system path to the backup files.
     * @param metadata Containing information about backup files
     * @return A [RestoreBackupResult] indicating the success or failure of the operation.
     */
    suspend operator fun invoke(backupRootPath: Path, metadata: BackupMetadata): RestoreBackupResult
}

@Suppress("TooManyFunctions", "LongParameterList", "NestedBlockDepth")
internal class RestoreWebBackupUseCaseImpl(
    private val kaliumFileSystem: KaliumFileSystem,
    private val selfUserId: UserId,
    private val persistMigratedMessages: PersistMigratedMessagesUseCase,
    private val restartSlowSyncProcessForRecovery: RestartSlowSyncProcessForRecoveryUseCase,
    private val migrationDAO: MigrationDAO,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) : RestoreWebBackupUseCase {

    override suspend operator fun invoke(backupRootPath: Path, metadata: BackupMetadata): RestoreBackupResult =
        withContext(dispatchers.io) {
            val version = metadata.version.toIntOrNull()
            if (version != null && version in OLDEST_SUPPORTED_WEB_VERSION..NEWEST_SUPPORTED_WEB_VERSION) {
                importWebBackup(backupRootPath, this)
            } else {
                Either.Left(IncompatibleBackup("invoke: The provided backup format is not supported"))
            }.fold({ error ->
                kaliumLogger.e("$TAG Failed to restore the backup, reason: ${error.cause}")
                RestoreBackupResult.Failure(error)
            }, {
                kaliumLogger.i("$TAG Successfully restored the backup")
                RestoreBackupResult.Success
            })
        }

    private suspend fun importWebBackup(
        filePath: Path,
        coroutineScope: CoroutineScope
    ): Either<RestoreBackupResult.BackupRestoreFailure, Unit> {
        return importMessages(filePath, coroutineScope)
            .map { tryImportConversations(filePath) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun tryImportConversations(filePath: Path) =
        kaliumFileSystem.listDirectories(filePath).firstOrNull { it.name == BACKUP_WEB_CONVERSATIONS_FILE_NAME }?.let { path ->
            kaliumFileSystem.source(path).buffer()
                .use {
                    val sequence = decodeBufferSequence<WebConversationContent>(it)
                    val iterator = sequence.iterator()
                    val migratedConversations = mutableListOf<Conversation>()
                    while (iterator.hasNext()) {
                        try {
                            val webConversation = iterator.next()
                            val migratedConversation = webConversation.toConversation(selfUserId)
                            if (migratedConversation != null) {
                                migratedConversations.add(migratedConversation)
                            }
                        } catch (exception: Exception) {
                            kaliumLogger.e("$TAG ${exception.message}")
                        }
                    }
                    if (migratedConversations.isNotEmpty()) {
                        wrapStorageRequest {
                            migrationDAO.insertConversation(migratedConversations.map(conversationMapper::fromMigrationModel))
                        }
                    }
                }
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun importMessages(filePath: Path, coroutineScope: CoroutineScope) = kaliumFileSystem.listDirectories(filePath)
        .firstOrNull { it.name == BACKUP_WEB_EVENTS_FILE_NAME }?.let { path ->
            kaliumFileSystem.source(path).buffer()
                .use {
                    val sequence = decodeBufferSequence<WebEventContent>(it)
                    val iterator = sequence.iterator()

                    val migratedMessagesBatch = mutableListOf<MigratedMessage>()
                    while (iterator.hasNext()) {
                        try {
                            val webContent = iterator.next()
                            val migratedMessage = webContent.toMigratedMessage(selfUserId.domain)
                            if (migratedMessage != null) {
                                migratedMessagesBatch.add(migratedMessage)
                            }
                        } catch (exception: Exception) {
                            kaliumLogger.e("$TAG ${exception.message}")
                        }

                        // send migrated messages in batches to not face any OOM errors
                        if (migratedMessagesBatch.size == MESSAGES_BATCH_SIZE) {
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
        } ?: Either.Left(BackupIOFailure("No valid json file found in the backup"))

    private companion object {
        const val TAG = "[RestoreWebBackupUseCase]"
        private const val MESSAGES_BATCH_SIZE = 1000
        private const val OLDEST_SUPPORTED_WEB_VERSION = 19
        private const val NEWEST_SUPPORTED_WEB_VERSION = 21
    }
}
