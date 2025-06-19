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

import com.wire.backup.ingest.ImportDataPager
import com.wire.backup.ingest.ImportResultPager
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.user.UserId
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
import kotlinx.coroutines.withContext
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.use

interface RestoreMPBackupUseCase {
    /**
     * Restores a valid previously created backup file in multiplatform format into the current database, respecting the current data
     * if there is any overlap.
     * @param backupFilePath The absolute file system path to the backup file.
     * @param password the password used to encrypt the original backup file. Null if the file was not encrypted.
     * @return A [RestoreBackupResult] indicating the success or failure of the operation.
     */
    suspend operator fun invoke(backupFilePath: Path, password: String?, onProgress: (Float) -> Unit): RestoreBackupResult
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
        try {
            val backupWorkDir = kaliumFileSystem.tempFilePath("${backupFilePath.name}-restore-workdir")
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
                is ImportResult.Success -> {
                    persistBackupData(result.pager) { currentPage, totalPages ->
                        onProgress(currentPage.toFloat() / totalPages)
                    }
                    RestoreBackupResult.Success
                }

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
            }.also {
                kaliumFileSystem.deleteContents(backupWorkDir)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            kaliumLogger.e("IO error during backup restore", e)
            RestoreBackupResult.Failure(
                RestoreBackupResult.BackupRestoreFailure.BackupIOFailure("IO error: ${e.message}")
            )
        }
    }

    private suspend fun persistBackupData(resultData: ImportResultPager, onProgress: (Int, Int) -> Unit) {
        resultData.use { pager ->

            var processedPageCount = 0

            pager.persistUsers { onProgress(processedPageCount++, pager.totalPagesCount) }
            pager.persistConversations { onProgress(processedPageCount++, pager.totalPagesCount) }
            pager.persistMessages { onProgress(processedPageCount++, pager.totalPagesCount) }
        }
    }

    private suspend fun ImportResultPager.persistUsers(onPageProcessed: () -> Unit) {
        usersPager.pages().forEach { page ->
            backupRepository.insertUsers(page.map { it.toUser() })
                .onFailure { error ->
                    kaliumLogger.e("Restore users error: $error")
                }
            onPageProcessed()
        }
    }

    private suspend fun ImportResultPager.persistConversations(onPageProcessed: () -> Unit) {
        conversationsPager.pages().forEach { page ->
            val conversations = page.map { it.toConversation() }

            backupRepository.insertConversations(conversations)
                .onFailure { error ->
                    kaliumLogger.e("Restore conversations error: $error")
                }

            conversations.forEach { conversation ->
                conversation.lastModifiedDate?.let { date ->
                    backupRepository.updateConversationLastModifiedDate(conversation.id, date)
                        .onFailure { error ->
                            kaliumLogger.e("Restore conversation last modified date error: $error")
                        }
                }
            }

            onPageProcessed()
        }
    }

    private suspend fun ImportResultPager.persistMessages(onPageProcessed: () -> Unit) {
        messagesPager.pages().forEach { page ->
            backupRepository.insertMessages(page.map { it.toMessage(selfUserId) })
                .onFailure { error ->
                    kaliumLogger.e("Restore messages error: $error")
                }
            onPageProcessed()
        }
    }
}

private fun <T> ImportDataPager<T>.pages(): Sequence<Array<T>> = sequence {
    while (hasMorePages()) {
        yield(nextPage())
    }
}
