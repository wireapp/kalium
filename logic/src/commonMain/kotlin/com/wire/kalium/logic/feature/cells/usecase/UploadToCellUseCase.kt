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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.cells.CellNode
import com.wire.kalium.logic.data.cells.PreCheckResult
import com.wire.kalium.logic.feature.cells.CellsRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import okio.Path

interface UploadToCellUseCase {
    /**
     * Uploads a file to the cell.
     * This use case will check if the file already exists in the cell and if it does, it will update the file name
     * with name suggested by Cells SDK.
     * @param path Path to the file to upload.
     * @param node New cell node for the file. Must have a valid UUID, VersionId and Path.
     * @param size Size of the file in bytes.
     * @param progress Callback to report the upload progress.
     * @return Deferred<Either<NetworkFailure, CellNode>>. Deferred result of the upload. Can be cancelled to cancel upload.
     */
    suspend operator fun invoke(
        path: Path,
        node: CellNode,
        size: Long,
        progress: (Float) -> Unit,
    ): Deferred<Either<NetworkFailure, CellNode>>
}

internal class UploadToCellUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val scope: CoroutineScope,
) : UploadToCellUseCase {

    override suspend operator fun invoke(
        path: Path,
        node: CellNode,
        size: Long,
        progress: (Float) -> Unit,
    ): Deferred<Either<NetworkFailure, CellNode>> =
        cellsRepository.preCheck(node).fold(
            fnR = { result ->
                val checkedNode = when (result) {
                    is PreCheckResult.FileExists -> node.copy(path = result.suggestedFilename)
                    PreCheckResult.Success -> node
                }
                scope.async {
                    cellsRepository.uploadFile(
                        path = path,
                        node = checkedNode,
                        onProgressUpdate = { uploaded -> progress(uploaded.toFloat() / size) }
                    ).map { checkedNode }
                }
            },
            fnL = { CompletableDeferred(it.left()) },
        )
}
