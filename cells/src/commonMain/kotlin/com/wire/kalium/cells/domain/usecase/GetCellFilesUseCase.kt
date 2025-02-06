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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either

public interface GetCellFilesUseCase {
    /**
     * Get the list of files in the cell.
     * @param cellName the name of the cell.
     * @return the list of files in the cell or [NetworkFailure].
     */
    public suspend operator fun invoke(cellName: String): Either<NetworkFailure, List<CellNode>>
}

internal class GetCellFilesUseCaseImpl(
    private val cellsRepository: CellsRepository
) : GetCellFilesUseCase {
    override suspend operator fun invoke(cellName: String): Either<NetworkFailure, List<CellNode>> {
        return cellsRepository.getFiles(cellName)
    }
}
