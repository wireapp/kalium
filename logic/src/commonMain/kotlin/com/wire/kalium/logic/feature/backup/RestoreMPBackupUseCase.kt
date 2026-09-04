/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.backup.ingest.ImportResultPager
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.message.reaction.MessageReactionWithUsers
import com.wire.kalium.logic.data.message.reaction.MessageReactions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.backup.mapper.toQualifiedIdOrNull
import com.wire.kalium.logic.feature.backup.mapper.toConversation
import com.wire.kalium.logic.feature.backup.mapper.toMessage
import com.wire.kalium.logic.feature.backup.mapper.toUser
import com.wire.kalium.logic.feature.backup.provider.ImportResult
import com.wire.kalium.logic.feature.backup.provider.MPBackupImporterProvider
import com.wire.kalium.logic.feature.backup.provider.MPBackupImporterProviderImpl
import com.wire.kalium.logic.util.ExtractFilesParam
import com.wire.kalium.logic.util.extractCompressedFile
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.use

public interface RestoreMPBackupUseCase {
    /**
     * Restores a valid previously created backup file in multiplatform format into the current database, respecting the current data
     * if there is any overlap.
     * @param backupFilePath The absolute file system path to the backup file.
     * @param password the password used to encrypt the original backup file. Null if the file was not encrypted.
     * @return A [RestoreBackupResult] indicating the success or failure of the operation.
     */
    public suspend operator fun invoke(backupFilePath: Path, password: String?, onProgress: (Float) -> Unit): RestoreBackupResult
}

internal class RestoreMPBackupUseCaseImpl(
    private val selfUserId: UserId,
    private val backupRepository: BackupRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val backupImporterProvider: MPBackupImporterProvider = MPBackupImporterProviderImpl(),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : RestoreMPBackupUseCase {

    override suspend fun invoke(
        backupFilePath: Path,
        password: String?,
        onProgress: (Float) -> Unit
    ): RestoreBackupResult = withContext(dispatchers.io) {
        val backupWorkDir = kaliumFileSystem.tempFilePath("${backupFilePath.name}-restore-workdir")
        try {
            kaliumFileSystem.deleteContents(backupWorkDir)

            val importer = backupImporterProvider.provideImporter(
                pathToWorkDirectory = backupWorkDir.toString(),
                backupFileUnzipper = { archivePath ->
                    extractCompressedFile(
                        inputSource = kaliumFileSystem.source(archivePath.toPath()),
                        outputRootPath = backupWorkDir,
                        param = ExtractFilesParam.All,
                        fileSystem = kaliumFileSystem,
                    ).fold(
                        { error("Failed to unzip: $it") },
                        { backupWorkDir.toString() }
                    )
                }
            )

            when (val result = importer.importFromFile(backupFilePath.toString(), password)) {
                is ImportResult.Success -> restoreImportedData(result.pager, onProgress)

                ImportResult.Failure.MissingOrWrongPassphrase -> RestoreBackupResult.Failure(
                    RestoreBackupResult.BackupRestoreFailure.InvalidPassword
                )

                ImportResult.Failure.ParsingFailure -> RestoreBackupResult.Failure(
                    RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Parsing failure")
                )

                is ImportResult.Failure.UnzippingError -> RestoreBackupResult.Failure(
                    RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Unzipping error")
                )

                is ImportResult.Failure.UnknownError -> RestoreBackupResult.Failure(
                    RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Unknown error")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            kaliumLogger.e("IO error during backup restore", e)
            RestoreBackupResult.Failure(
                RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("IO error: ${e.message}")
            )
        } finally {
            withContext(NonCancellable) {
                kaliumFileSystem.deleteContents(backupWorkDir)
            }
        }
    }

    private suspend fun restoreImportedData(
        pager: ImportResultPager,
        onProgress: (Float) -> Unit,
    ): RestoreBackupResult {
        val failure = persistBackupData(pager) { currentPage, totalPages ->
            val progress = if (totalPages == 0) 1f else currentPage.toFloat() / totalPages
            withContext(dispatchers.main) {
                onProgress(progress.coerceIn(0f, 1f))
            }
        }
        if (failure != null) {
            kaliumLogger.e("Failed to persist backup data: $failure")
            return RestoreBackupResult.Failure(
                RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("Failed to persist backup data")
            )
        }
        currentCoroutineContext().ensureActive()
        return RestoreBackupResult.Success
    }

    private suspend fun persistBackupData(
        resultData: ImportResultPager,
        onProgress: suspend (Int, Int) -> Unit,
    ): CoreFailure? {
        resultData.use { pager ->
            var processedPageCount = 0
            val onPageProcessed: suspend () -> Unit = {
                processedPageCount++
                onProgress(processedPageCount, pager.totalPagesCount)
            }

            val failure = pager.persistUsers(onPageProcessed)
                ?: pager.persistConversations(onPageProcessed)
                ?: pager.persistMessages(onPageProcessed)
                ?: pager.persistReactions(onPageProcessed)
            if (failure == null) {
                currentCoroutineContext().ensureActive()
                if (processedPageCount == 0 || processedPageCount < pager.totalPagesCount) {
                    onProgress(1, 1)
                }
            }
            return failure
        }
    }

    private suspend fun ImportResultPager.persistUsers(onPageProcessed: suspend () -> Unit): CoreFailure? {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!usersPager.hasMorePages()) break
            currentCoroutineContext().ensureActive()
            val page = usersPager.nextPage()
            val result = withContext(NonCancellable) {
                backupRepository.insertUsers(page.mapNotNull { it.toUser() })
            }
            currentCoroutineContext().ensureActive()
            result.fold({ return it }, {})
            onPageProcessed()
        }
        return null
    }

    private suspend fun ImportResultPager.persistConversations(onPageProcessed: suspend () -> Unit): CoreFailure? {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!conversationsPager.hasMorePages()) break
            currentCoroutineContext().ensureActive()
            val page = conversationsPager.nextPage()
            val conversations = page.mapNotNull { it.toConversation() }
            val result = withContext(NonCancellable) {
                backupRepository.insertConversations(conversations)
            }
            currentCoroutineContext().ensureActive()
            result.fold({ return it }, {})
            onPageProcessed()
        }
        return null
    }

    private suspend fun ImportResultPager.persistMessages(onPageProcessed: suspend () -> Unit): CoreFailure? {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!messagesPager.hasMorePages()) break
            currentCoroutineContext().ensureActive()
            val page = messagesPager.nextPage()
            val result = withContext(NonCancellable) {
                backupRepository.insertMessages(page.mapNotNull { it.toMessage(selfUserId) })
            }
            currentCoroutineContext().ensureActive()
            result.fold({ return it }, {})
            onPageProcessed()
        }
        return null
    }

    private suspend fun ImportResultPager.persistReactions(onPageProcessed: suspend () -> Unit): CoreFailure? {
        while (true) {
            currentCoroutineContext().ensureActive()
            if (!reactionsPager.hasMorePages()) break
            currentCoroutineContext().ensureActive()
            val page = reactionsPager.nextPage()
            val result = withContext(NonCancellable) {
                backupRepository.insertReactions(
                    reactions = page.mapNotNull { reaction ->
                        val conversationId = reaction.conversationId
                            .toQualifiedIdOrNull("restore.reaction.conversationId")
                            ?: return@mapNotNull null

                        MessageReactions(
                            messageId = reaction.messageId,
                            conversationId = conversationId,
                            reactions = reaction.emojiReactions.map { emojiReaction ->
                                MessageReactionWithUsers(
                                    emoji = emojiReaction.emoji,
                                    users = emojiReaction.users.mapNotNull {
                                        it.toQualifiedIdOrNull("restore.reaction.userId")
                                    }
                                )
                            }
                        )
                    }
                )
            }
            currentCoroutineContext().ensureActive()
            result.fold({ return it }, {})
            onPageProcessed()
        }
        return null
    }
}
