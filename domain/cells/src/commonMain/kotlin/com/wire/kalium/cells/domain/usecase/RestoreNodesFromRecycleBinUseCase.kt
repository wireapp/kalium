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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right

public interface RestoreNodesFromRecycleBinUseCase {
    /**
     * Bulk version of [RestoreNodeFromRecycleBinUseCase]: restores several nodes from the recycle bin in a single
     * request, then makes them available again as message attachments.
     *
     * There is no rollback: if the request fails midway, some of the nodes may still have been restored. Their attachment
     * status is left untouched then, as there is no telling which ones; it's updated when they are next refreshed.
     *
     * @param uuids the nodes to restore, each back to where it was deleted from.
     */
    public suspend operator fun invoke(uuids: List<String>): Either<CoreFailure, Unit>
}

internal class RestoreNodesFromRecycleBinUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
) : RestoreNodesFromRecycleBinUseCase {

    override suspend fun invoke(uuids: List<String>): Either<CoreFailure, Unit> {
        if (uuids.isEmpty()) return Unit.right()
        return cellsRepository.restoreNodes(uuids)
            .onSuccess { uuids.forEach { attachmentsRepository.refreshRestoredAttachmentStatus(it) } }
    }
}
