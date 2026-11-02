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

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

public interface EmptyRecycleBinUseCase {
    /**
     * Permanently deletes everything in the recycle bin of a conversation, in a single request.
     *
     * There is no local cleanup: the local data of the nodes in the recycle bin was already cleaned up when they were
     * moved there. There is no rollback either: if the request fails midway, some of them may already be gone.
     *
     * @param conversationId the conversation whose recycle bin to empty.
     */
    public suspend operator fun invoke(conversationId: String): Either<CoreFailure, Unit>
}

internal class EmptyRecycleBinUseCaseImpl(
    private val cellsRepository: CellsRepository,
) : EmptyRecycleBinUseCase {

    override suspend fun invoke(conversationId: String): Either<CoreFailure, Unit> =
        cellsRepository.deleteFiles(listOf("$conversationId/$RECYCLE_BIN_FOLDER"), permanentDelete = true)

    private companion object {
        const val RECYCLE_BIN_FOLDER = "recycle_bin"
    }
}
