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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map

public fun interface GetEditorUrlUseCase {
    /**
     * Use case to get the editor URL for a given [com.wire.kalium.cells.domain.model.Node].
     * @param nodeUuid The unique identifier of the node.
     * @return the result of the get editor URL operation, or null if no URL is available.
     */
    public suspend operator fun invoke(nodeUuid: String): Either<CoreFailure, String?>
}

internal class GetEditorUrlUseCaseImpl(
    private val repository: CellsRepository
) : GetEditorUrlUseCase {

    private companion object {
        const val COLLABORA_EDITOR_URL = "collabora"
    }

    override suspend fun invoke(nodeUuid: String): Either<CoreFailure, String?> =
        repository.getEditorUrl(nodeUuid, COLLABORA_EDITOR_URL)
            .map { it.takeIf { it.isNotEmpty() } }
}
