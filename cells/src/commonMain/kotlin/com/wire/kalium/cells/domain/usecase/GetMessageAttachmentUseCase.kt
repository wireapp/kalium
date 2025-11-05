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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.MessageAttachment
import io.mockative.Mockable

/**
 * Use case to get a message attachment by its asset ID.
 */
@Mockable
public interface GetMessageAttachmentUseCase {
    public suspend operator fun invoke(
        assetId: String,
    ): Either<StorageFailure, MessageAttachment>
}

internal class GetMessageAttachmentUseCaseImpl(
    private val cellAttachmentsRepository: CellAttachmentsRepository,
) : GetMessageAttachmentUseCase {

    override suspend operator fun invoke(assetId: String): Either<StorageFailure, MessageAttachment> =
        cellAttachmentsRepository.getAttachment(assetId = assetId)
}
