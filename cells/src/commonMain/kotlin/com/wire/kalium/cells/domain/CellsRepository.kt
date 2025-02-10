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
package com.wire.kalium.cells.domain

import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.PreCheckResult
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import okio.Path

internal interface CellsRepository {
    suspend fun preCheck(nodePath: String): Either<NetworkFailure, PreCheckResult>
    suspend fun uploadFile(path: Path, node: CellNode, onProgressUpdate: (Long) -> Unit): Either<NetworkFailure, Unit>
    suspend fun getFiles(cellName: String): Either<NetworkFailure, List<CellNode>>
    suspend fun deleteFile(node: CellNode): Either<NetworkFailure, Unit>
    suspend fun cancelDraft(node: CellNode): Either<NetworkFailure, Unit>
    suspend fun publishDraft(node: CellNode): Either<NetworkFailure, Unit>
}
