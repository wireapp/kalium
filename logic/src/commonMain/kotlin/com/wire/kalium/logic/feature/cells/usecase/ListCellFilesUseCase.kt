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
package com.wire.kalium.logic.feature.cells.usecase

import com.wire.cells.WireCellsClient
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.right

interface ListCellFilesUseCase {
    suspend operator fun invoke(cellName: String): Either<NetworkFailure, List<String>>
}

internal class ListCellFilesUseCaseImpl(
    private val cellsClient: WireCellsClient
) : ListCellFilesUseCase {
    override suspend operator fun invoke(cellName: String): Either<NetworkFailure, List<String>> = try {
        cellsClient.list(cellName = cellName).right()
    } catch (e: Exception) {
        Either.Left(NetworkFailure.NoNetworkConnection(e))
    }
}
