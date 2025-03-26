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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.Path

public interface DownloadCellFileUseCase {
    public suspend operator fun invoke(
        assetId: String,
        outFilePath: Path,
        assetSize: Long,
        remoteFilePath: String? = null,
        onProgressUpdate: (Long) -> Unit
    ): Either<CoreFailure, Unit>
}

/**
 * Download an asset file from the wire cell server.
 */
internal class DownloadCellFileUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : DownloadCellFileUseCase {
    /**
     * Download an asset file from the wire cell server.
     * The asset transfer status is updated in the database.
     * The local path of the downloaded file is saved in the database.
     *
     * @param assetId The uuid of the cell asset to download.
     * @param outFilePath The path to save the downloaded file.
     * @param onProgressUpdate Callback to receive download progress updates.
     * @return download operation result
     */
    override suspend operator fun invoke(
        assetId: String,
        outFilePath: Path,
        assetSize: Long,
        remoteFilePath: String?,
        onProgressUpdate: (Long) -> Unit,
    ): Either<CoreFailure, Unit> = withContext(dispatchers.io) {
        attachmentsRepository.getAssetPath(assetId).fold(
            {
                // Attachment asset not found
                // Try to download standalone file (not received as attachment in conversation).
                remoteFilePath?.let {
                    downloadFromRemotePath(assetId, outFilePath, assetSize, it, onProgressUpdate)
                } ?: Either.Left(StorageFailure.DataNotFound)
            },
            { path ->
                (remoteFilePath ?: path)?.let {
                    attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.DOWNLOAD_IN_PROGRESS)
                    cellsRepository.downloadFile(outFilePath, it, onProgressUpdate)
                        .onSuccess {
                            attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.SAVED_INTERNALLY)
                            attachmentsRepository.saveLocalPath(assetId, outFilePath.toString())
                        }
                        .onFailure {
                            attachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.FAILED_DOWNLOAD)
                        }
                } ?: Either.Left(StorageFailure.DataNotFound)
            }
        )
    }

    private suspend fun downloadFromRemotePath(
        assetId: String,
        outFilePath: Path,
        assetSize: Long,
        path: String,
        onProgressUpdate: (Long) -> Unit,
    ) = cellsRepository.downloadFile(outFilePath, path, onProgressUpdate)
            .onSuccess { attachmentsRepository.saveStandaloneAssetPath(assetId, outFilePath.toString(), assetSize) }

}
