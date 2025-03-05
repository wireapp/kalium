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
import com.wire.kalium.cells.domain.model.NodeIdAndVersion
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageAttachment

public interface PublishAttachmentsUseCase {
    /**
     * For TESTING purposes only.
     * Use case for publishing all draft attachments and creating public URLs.
     */
    public suspend operator fun invoke(attachments: List<MessageAttachment>): Either<NetworkFailure, Unit>
}

internal class PublishAttachmentsUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
) : PublishAttachmentsUseCase {

    @Suppress("ReturnCount")
    override suspend fun invoke(attachments: List<MessageAttachment>): Either<NetworkFailure, Unit> {
        val assets = attachments.filterIsInstance<CellAssetContent>().map {
            NodeIdAndVersion(it.id, it.versionId)
        }.toList()
        return cellsRepository.publishDrafts(assets)
    }
}
