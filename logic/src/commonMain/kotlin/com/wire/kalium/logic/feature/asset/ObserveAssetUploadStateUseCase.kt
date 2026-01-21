/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Use case observing statuses of assets when uploading and downloading.
 */
public interface ObserveAssetUploadStateUseCase {
    public suspend operator fun invoke(): Flow<Boolean>
}

internal class ObserveAssetUploadStateUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val attachmentDraftRepository: MessageAttachmentDraftRepository,
) : ObserveAssetUploadStateUseCase {
    override suspend fun invoke(): Flow<Boolean> {
        return combine(
            observeRegularAssetsUpload(),
            observeAttachmentDraftsUpload()
        ) { regularAssetsUploading, draftAttachmentsUploading ->
            regularAssetsUploading || draftAttachmentsUploading
        }.distinctUntilChanged()
    }

    private suspend fun observeRegularAssetsUpload() =
        messageRepository.observeAssetStatuses()
            .map { result ->
                result.fold(
                    { false },
                    { statuses ->
                        statuses.any { status -> status == AssetTransferStatus.UPLOAD_IN_PROGRESS }
                    }
                )
            }

    private suspend fun observeAttachmentDraftsUpload() =
        attachmentDraftRepository.observeAllDrafts()
            .map { result ->
                result.fold(
                    { false },
                    { drafts ->
                        drafts.any { draft -> draft.uploadStatus == AttachmentUploadStatus.UPLOADING }
                    }
                )
            }
}
