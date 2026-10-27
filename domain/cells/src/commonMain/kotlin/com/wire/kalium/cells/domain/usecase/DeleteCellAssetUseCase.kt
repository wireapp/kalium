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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.SYSTEM

/**
 * Delete asset with given asset id from server and local storage.
 *
 * This use case is used for deleting assets from global/conversation lists.
 * Attachment data is not removed from the database to keep showing the attachment, but the
 * transfer status is set to NOT_FOUND.
 */
public interface DeleteCellAssetUseCase {
    /**
     * @param localPath the local copy of the file to remove, null for folders and files never downloaded.
     * @param folderPath for a folder, its remote path, to also clean up the local copies of the files stored under it.
     * Null for a file.
     */
    public suspend operator fun invoke(assetId: String, localPath: String?, folderPath: String? = null): Either<CoreFailure, Unit>
}

internal class DeleteCellAssetUseCaseImpl(
    private val cellsRepository: CellsRepository,
    cellAttachmentsRepository: CellAttachmentsRepository,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : DeleteCellAssetUseCase {

    private val localCleanup = CellAssetLocalCleanup(cellAttachmentsRepository, fileSystem, dispatchers)

    override suspend fun invoke(assetId: String, localPath: String?, folderPath: String?) =
        cellsRepository.deleteFile(assetId)
            // Deleted on the server: clean up even if the caller is cancelled meanwhile, not to keep stale local data.
            .flatMap { withContext(NonCancellable) { localCleanup(assetId, localPath, folderPath) } }
}
