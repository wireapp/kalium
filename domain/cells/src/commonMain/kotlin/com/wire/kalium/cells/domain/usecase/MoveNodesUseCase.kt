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
import com.wire.kalium.cells.domain.model.NodeLocator
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

public interface MoveNodesUseCase {
    /**
     * Use case to move several [com.wire.kalium.cells.domain.model.Node]s into the same folder in a single request.
     * @param nodes the nodes to move, with their current path.
     * @param targetPath the path of the folder the nodes should be moved into.
     * @return the result of the move operation.
     */
    public suspend operator fun invoke(nodes: List<NodeLocator>, targetPath: String): Either<CoreFailure, Unit>
}

internal class MoveNodesUseCaseImpl(
    private val cellsRepository: CellsRepository,
) : MoveNodesUseCase {
    override suspend fun invoke(nodes: List<NodeLocator>, targetPath: String): Either<CoreFailure, Unit> =
        cellsRepository.moveNodes(nodes = nodes, targetPath = targetPath)
}
