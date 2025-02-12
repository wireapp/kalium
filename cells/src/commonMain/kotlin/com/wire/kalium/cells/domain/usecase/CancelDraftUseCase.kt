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

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either

public interface CancelDraftUseCase {
    /**
     * Cancels the draft of the cell node.
     * @param node Cell node to cancel the draft for.
     * @return Either<NetworkFailure, Unit>. Result of the operation.
     */
    public suspend operator fun invoke(node: CellNode): Either<NetworkFailure, Unit>
}

internal class CancelDraftUseCaseImpl(
    private val cellsRepository: CellsRepository
) : CancelDraftUseCase {
    override suspend operator fun invoke(node: CellNode): Either<NetworkFailure, Unit> {
        return cellsRepository.cancelDraft(node)
    }
}
