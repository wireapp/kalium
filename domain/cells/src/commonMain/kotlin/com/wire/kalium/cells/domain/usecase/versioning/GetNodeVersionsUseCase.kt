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
package com.wire.kalium.cells.domain.usecase.versioning

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.NodeVersion
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

public interface GetNodeVersionsUseCase {
    /**
     * Use case to get the versions of a [com.wire.kalium.cells.domain.model.Node] within a specific cell.
     * @param uuid The unique identifier of the cell.
     * @return the result of the get node versions operation.
     */
    public suspend operator fun invoke(uuid: String): Either<CoreFailure, List<NodeVersion>>
}

internal class GetNodeVersionsUseCaseImpl(
    private val cellsRepository: CellsRepository
) : GetNodeVersionsUseCase {
    override suspend fun invoke(uuid: String): Either<CoreFailure, List<NodeVersion>> =
        cellsRepository.getNodeVersions(uuid)
}
