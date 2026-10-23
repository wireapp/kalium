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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.NodeToDelete
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import okio.FileSystem
import okio.SYSTEM

public interface DeleteCellAssetsUseCase {
    /**
     * Bulk version of [DeleteCellAssetUseCase]: deletes several nodes from the server in a single request, then cleans up
     * their local data the same way.
     *
     * There is no rollback: if the request fails midway, some of the nodes may still have been deleted. Their local data
     * is left untouched then, as there is no telling which ones; it's cleaned up when they are next refreshed and found
     * missing (see [RefreshCellAssetStateUseCase]).
     *
     * @param permanentDelete false to move the nodes to the recycle bin, true to delete them for good, e.g. to clean up
     * the recycle bin itself.
     * @return the result of the server request. Once it succeeded, local cleanup is best effort and doesn't fail the
     * operation: the nodes are deleted either way.
     */
    public suspend operator fun invoke(nodes: List<NodeToDelete>, permanentDelete: Boolean = false): Either<CoreFailure, Unit>
}

internal class DeleteCellAssetsUseCaseImpl(
    private val cellsRepository: CellsRepository,
    cellAttachmentsRepository: CellAttachmentsRepository,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : DeleteCellAssetsUseCase {

    private val localCleanup = CellAssetLocalCleanup(cellAttachmentsRepository, fileSystem, dispatchers)

    override suspend fun invoke(nodes: List<NodeToDelete>, permanentDelete: Boolean): Either<CoreFailure, Unit> {
        if (nodes.isEmpty()) return Unit.right()
        return cellsRepository.deleteFiles(nodes.map { it.locator.path }, permanentDelete)
            // Done for every node even if it fails for one of them, see the result contract above.
            .onSuccess { nodes.forEach { localCleanup(it.locator.uuid, it.localPath) } }
    }
}
