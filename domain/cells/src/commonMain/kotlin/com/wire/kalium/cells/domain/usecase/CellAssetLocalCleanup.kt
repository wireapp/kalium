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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * Local side of deleting a cell asset, once it has been deleted on the server.
 * Shared by [DeleteCellAssetUseCase] and [DeleteCellAssetsUseCase] so both clean up the same way.
 */
internal class CellAssetLocalCleanup(
    private val cellAttachmentsRepository: CellAttachmentsRepository,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) {

    /**
     * @param localPath the local copy of the file to remove, null if there is none.
     * @return a failure if the database cleanup failed. Failing to remove the local copy doesn't count: the asset is
     * gone either way, only a stale file is left behind.
     */
    suspend operator fun invoke(assetId: String, localPath: String?): Either<StorageFailure, Unit> =
        // Keep asset data but set transfer status to NOT_FOUND to show attachment as not available
        cellAttachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_FOUND)
            .flatMap {
                // Delete asset from the database if it was not received as message attachment
                cellAttachmentsRepository.deleteStandaloneAsset(assetId)
            }
            .onSuccess {
                deleteLocalFile(localPath)
            }

    private suspend fun deleteLocalFile(localPath: String?) {
        localPath?.let {
            withContext(dispatchers.io) {
                try {
                    fileSystem.delete(it.toPath())
                } catch (_: IOException) {
                    // A missing file doesn't throw; this is e.g. a permission error, not worth failing the delete for.
                }
            }
        }
    }
}
