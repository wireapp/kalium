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
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.CellNodeType
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.cells.domain.model.toFileModel
import com.wire.kalium.cells.domain.model.toFolderModel
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import okio.FileSystem
import okio.SYSTEM

public interface CheckCellNodeAvailabilityUseCase {
    /**
     * Checks that a node listed earlier is still available, e.g. before opening it: the lists aren't updated when nodes
     * change elsewhere. When it's [CellNodeAvailability.Gone], its local data is cleaned up like for a delete.
     * @param inRecycleBin true when the node was listed in the recycle bin, where being recycled is expected: it's only
     * gone once deleted for good.
     */
    public suspend operator fun invoke(uuid: String, inRecycleBin: Boolean = false): CellNodeAvailability
}

internal class CheckCellNodeAvailabilityUseCaseImpl(
    private val cellsRepository: CellsRepository,
    cellAttachmentsRepository: CellAttachmentsRepository,
    fileSystem: FileSystem = FileSystem.SYSTEM,
    dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : CheckCellNodeAvailabilityUseCase {

    private val localCleanup = CellAssetLocalCleanup(cellAttachmentsRepository, fileSystem, dispatchers)

    override suspend fun invoke(uuid: String, inRecycleBin: Boolean): CellNodeAvailability =
        cellsRepository.getNode(uuid).fold(
            { failure -> if (failure.isAssetNotFound()) gone(uuid) else CellNodeAvailability.Unknown(failure) },
            { node ->
                if (node.isRecycled && !inRecycleBin) gone(uuid) else CellNodeAvailability.Available(node.toModel())
            }
        )

    private fun CellNode.toModel(): Node = if (type == CellNodeType.FOLDER.value) toFolderModel() else toFileModel()

    private suspend fun gone(uuid: String): CellNodeAvailability {
        localCleanup(uuid, localPath = null)
        return CellNodeAvailability.Gone
    }
}

/** Whether a node is still available on the server, see [CheckCellNodeAvailabilityUseCase]. */
public sealed interface CellNodeAvailability {
    /** @param node the node as it is now on the server: for a file, with fresh content and preview URLs. */
    public data class Available(val node: Node) : CellNodeAvailability

    /** The node was deleted, moved to the recycle bin (unless checked from there), or access to it was lost. */
    public data object Gone : CellNodeAvailability

    /** It couldn't be checked, e.g. offline or a server error: the node may well still be there. */
    public data class Unknown(val failure: CoreFailure) : CellNodeAvailability
}
