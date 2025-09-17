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
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent

public interface RestoreNodeFromRecycleBinUseCase {
    public suspend operator fun invoke(uuid: String): Either<CoreFailure, Unit>
}

internal class RestoreNodeFromRecycleBinUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
) : RestoreNodeFromRecycleBinUseCase {
    override suspend fun invoke(uuid: String): Either<CoreFailure, Unit> =
        cellsRepository.restoreNode(uuid = uuid)
            .onSuccess {
                refreshLocalAttachmentStatus(uuid)
            }

    private suspend fun refreshLocalAttachmentStatus(uuid: String) {

        val attachment = attachmentsRepository.getAttachment(uuid).getOrNull() as? CellAssetContent ?: return

        val status = if (attachment.localPath.isNullOrEmpty()) {
            AssetTransferStatus.NOT_DOWNLOADED
        } else {
            AssetTransferStatus.SAVED_INTERNALLY
        }

        attachmentsRepository.setAssetTransferStatus(uuid, status)
    }
}
