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
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.Path

public class DownloadCellFileUseCase internal constructor(
    private val cellsRepository: CellsRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) {
    public suspend operator fun invoke(
        assetId: String,
        outFilePath: Path,
        onProgressUpdate: (Long) -> Unit,
    ) {
        withContext(dispatchers.io) {
            cellsRepository.getAssetPath(assetId).map { path ->
                path?.let {
                    cellsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.DOWNLOAD_IN_PROGRESS)
                    cellsRepository.downloadFile(outFilePath, path, onProgressUpdate)
                        .onSuccess {
                            cellsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.SAVED_INTERNALLY)
                            cellsRepository.saveLocalPath(assetId, outFilePath.toString())
                        }
                        .onFailure {
                            cellsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.FAILED_DOWNLOAD)
                        }
                }
            }
        }
    }
}
