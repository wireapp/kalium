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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

/**
 * Delete asset with given asset id from server and local storage.
 */
public interface DeleteCellAssetUseCase {
    public suspend operator fun invoke(assetId: String, localPath: String?): Either<CoreFailure, Unit>
}

internal class DeleteCellAssetUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val cellAttachmentsRepository: CellAttachmentsRepository,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : DeleteCellAssetUseCase {

    override suspend fun invoke(assetId: String, localPath: String?) =
        cellsRepository.deleteFile(assetId)
            .flatMap {
                cellAttachmentsRepository.setAssetTransferStatus(assetId, AssetTransferStatus.NOT_FOUND)
            }
            .flatMap {
                cellAttachmentsRepository.deleteStandaloneAsset(assetId)
            }
            .onSuccess {
                deleteLocalFile(localPath)
            }

    private suspend fun deleteLocalFile(localPath: String?) {
        localPath?.let {
            withContext(dispatchers.io) {
                fileSystem.delete(it.toPath())
            }
        }
    }
}
