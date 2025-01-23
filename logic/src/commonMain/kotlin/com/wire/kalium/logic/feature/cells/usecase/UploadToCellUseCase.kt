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
import com.wire.kalium.logic.kaliumLogger
import okio.Path

interface UploadToCellUseCase {
    suspend operator fun invoke(
        cellName: String,
        fileName: String,
        path: Path,
        onProgressUpdate: (Long) -> Unit
    ): Either<NetworkFailure, Unit>
}

internal class UploadToCellUseCaseImpl(
    private val cellsClient: WireCellsClient,
) : UploadToCellUseCase {

    override suspend operator fun invoke(
        cellName: String,
        fileName: String,
        path: Path,
        onProgressUpdate: (Long) -> Unit
    ): Either<NetworkFailure, Unit> =
        try {
            cellsClient.upload(
                cellName = cellName,
                fileName = fileName,
                path = path,
                onProgressUpdate = onProgressUpdate,
            )
            Either.Right(Unit)
        } catch (e: Exception) {
            kaliumLogger.e("Failed to upload file to cell", e)
            Either.Left(NetworkFailure.NoNetworkConnection(e))
        }
}
