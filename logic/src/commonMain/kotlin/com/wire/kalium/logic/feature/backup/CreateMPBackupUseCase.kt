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

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.dump.BackupExportResult
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.backup.BackupConstants.createBackupFileName
import com.wire.kalium.logic.feature.backup.CreateBackupResult.Failure
import com.wire.kalium.logic.feature.backup.mapper.toBackupConversation
import com.wire.kalium.logic.feature.backup.mapper.toBackupMessage
import com.wire.kalium.logic.feature.backup.mapper.toBackupUser
import com.wire.kalium.logic.feature.backup.provider.BackupExporter
import com.wire.kalium.logic.feature.backup.provider.MPBackupExporterProvider
import com.wire.kalium.logic.feature.backup.provider.MPBackupExporterProviderImpl
import com.wire.kalium.logic.util.createCompressedFile
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.use

interface CreateMPBackupUseCase {
    /**
     * Creates a compressed backup file in multiplatform format. This file can be encrypted
     * with the provided password if password is not empty.
     * @param password The password to encrypt the backup file with. If empty, the file will be unencrypted.
     */
    suspend operator fun invoke(password: String, onProgress: (Float) -> Unit): CreateBackupResult
}

internal class CreateMPBackupUseCaseImpl(
    private val backupRepository: BackupRepository,
    private val userRepository: UserRepository,
    private val kaliumFileSystem: KaliumFileSystem,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val exporterProvider: MPBackupExporterProvider = MPBackupExporterProviderImpl(),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : CreateMPBackupUseCase {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun invoke(password: String, onProgress: (Float) -> Unit): CreateBackupResult = withContext(dispatchers.io) {
        try {

            val selfUser = userRepository.getSelfUser().getOrNull() ?: error("Self user not found")
            val backupFileName = createBackupFileName(selfUser)
            val backupWorkDir = kaliumFileSystem.tempFilePath("$backupFileName-create-workdir")
            val mpBackupExporter = createBackupExporter(selfUser, backupFileName, backupWorkDir.toString())
            var pageIndex = 0

            with(backupRepository) {
                awaitAll(
                    coroutineScope {
                        async {
                            getUsers().forEach { user ->
                                mpBackupExporter.add(user.toBackupUser())
                            }
                        }
                    },
                    coroutineScope {
                        async {
                            getConversations().forEach { conversation ->
                                mpBackupExporter.add(conversation.toBackupConversation())
                            }
                        }
                    },
                    coroutineScope {
                        async {
                            getMessages { totalPages, page ->
                                page.mapNotNull(Message::toBackupMessage)
                                    .forEach { mpBackupExporter.add(it) }
                                onProgress(pageIndex++.toFloat() / totalPages)
                            }
                        }
                    },
                )
            }

            when (val result = mpBackupExporter.finalize(password)) {
                is BackupExportResult.Success ->
                    CreateBackupResult.Success(
                        backupFilePath = result.pathToOutputFile.toPath(),
                        backupFileName = backupFileName,
                    )
                else -> {
                    deleteBackupFiles(backupWorkDir)
                    Failure(CoreFailure.Unknown(null))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            kaliumLogger.e("Failed to create backup", e)
            Failure(CoreFailure.Unknown(e))
        }
    }

    private fun createBackupFileName(selfUser: SelfUser) = createBackupFileName(
        userHandle = selfUser.handle?.replace(".", "-"),
        timestampIso = DateTimeUtil.currentSimpleDateTimeString(),
    )

    private fun createBackupExporter(selfUser: SelfUser, backupFileName: String, backupWorkDir: String): BackupExporter =
        exporterProvider.provideExporter(
            selfUserId = BackupQualifiedId(selfUser.id.value, selfUser.id.domain),
            workDirectory = backupWorkDir,
            outputDirectory = backupWorkDir,
            fileZipper = { entries, workDirectory ->
                val backupFilePath = workDirectory / backupFileName
                fileSystem.sink(backupFilePath).use { output ->
                    createCompressedFile(
                        files = entries.map { file ->
                            val path = file.toPath()
                            fileSystem.source(path) to path.name
                        },
                        outputSink = output
                    )
                }.fold(
                    { error ->
                        error("Failed to create compressed file: $error")
                    },
                    {
                        backupFilePath.toString()
                    }
                )
            }
        )

    private fun deleteBackupFiles(backupFilePath: Path) {
        if (fileSystem.exists(backupFilePath))
            fileSystem.delete(backupFilePath)
    }
}
