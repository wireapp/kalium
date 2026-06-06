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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.data.FileFilters
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path
import okio.SYSTEM

/**
 * Uploads and reads backup files in a Wire Cells-enabled conversation.
 */
public data class BackupCellFile(
    public val uuid: String,
    public val versionId: String,
    public val path: String,
)

/**
 * Provides the Cells file operations needed by online backups.
 */
public interface BackupCellFileUseCase {
    public suspend fun upload(
        conversationId: ConversationId,
        localPath: Path,
        fileName: String,
    ): Either<CoreFailure, BackupCellFile>

    public suspend fun listMetadataFiles(conversationId: ConversationId): Either<CoreFailure, List<BackupCellFile>>

    public suspend fun download(
        remotePath: String,
        outputPath: Path,
    ): Either<CoreFailure, Unit>
}

internal class BackupCellFileUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
    private val conversationRepository: CellConversationRepository,
    private val uploadManager: CellUploadManager,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : BackupCellFileUseCase {

    override suspend fun upload(
        conversationId: ConversationId,
        localPath: Path,
        fileName: String,
    ): Either<CoreFailure, BackupCellFile> =
        getCellName(conversationId).flatMap { cellName ->
            val assetSize = fileSystem.metadata(localPath).size ?: 0L
            uploadManager.upload(localPath, assetSize, "$cellName/$fileName")
                .mapLeft { it as CoreFailure }
                .flatMap { node ->
                    waitUntilUploaded(node.uuid).flatMap {
                        cellsRepository.publishDrafts(listOf(NodeIdAndVersion(node.uuid, node.versionId)))
                            .mapLeft { it as CoreFailure }
                            .map {
                                BackupCellFile(
                                    uuid = node.uuid,
                                    versionId = node.versionId,
                                    path = node.path,
                                )
                            }
                    }
                }
        }

    override suspend fun listMetadataFiles(conversationId: ConversationId): Either<CoreFailure, List<BackupCellFile>> =
        getCellName(conversationId).flatMap { cellName ->
            cellsRepository.getNodesByPath(
                query = METADATA_SUFFIX,
                path = cellName,
                fileFilters = FileFilters(),
            ).mapLeft { it as CoreFailure }
                .map { nodes ->
                    nodes
                        .filter { it.path.endsWith(METADATA_SUFFIX) }
                        .map {
                            BackupCellFile(
                                uuid = it.uuid,
                                versionId = it.versionId,
                                path = it.path,
                            )
                        }
                }
        }

    override suspend fun download(
        remotePath: String,
        outputPath: Path,
    ): Either<CoreFailure, Unit> =
        cellsRepository.downloadFile(outputPath, remotePath, onProgressUpdate = {})
            .mapLeft { it as CoreFailure }

    private suspend fun getCellName(conversationId: ConversationId): Either<CoreFailure, String> =
        conversationRepository.getCellName(conversationId)
            .mapLeft { it as CoreFailure }
            .flatMap { cellName ->
                if (cellName == null) {
                    Either.Left(StorageFailure.DataNotFound)
                } else {
                    Either.Right(cellName)
                }
            }

    private suspend fun waitUntilUploaded(nodeUuid: String): Either<CoreFailure, Unit> {
        val uploadEvents = uploadManager.observeUpload(nodeUuid)
            ?: return Either.Left(NetworkFailure.ServerMiscommunication(IllegalStateException("Cells upload not found")))

        return when (uploadEvents.first { it !is CellUploadEvent.UploadProgress }) {
            CellUploadEvent.UploadCompleted -> Either.Right(Unit)
            CellUploadEvent.UploadCancelled -> Either.Left(NetworkFailure.ServerMiscommunication(IllegalStateException("Cells upload cancelled")))
            CellUploadEvent.UploadError -> Either.Left(NetworkFailure.ServerMiscommunication(IllegalStateException("Cells upload failed")))
            is CellUploadEvent.UploadProgress -> error("Progress events are filtered out")
        }
    }

    private companion object {
        const val METADATA_SUFFIX = ".metadata.json"
    }
}
