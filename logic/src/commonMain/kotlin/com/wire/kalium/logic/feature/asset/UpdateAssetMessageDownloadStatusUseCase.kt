/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message.DownloadStatus
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold

interface UpdateAssetMessageDownloadStatusUseCase {
    /**
     * Function that allows update an asset message download status. This field indicates whether the asset has been downloaded locally
     * already or not.
     *
     * @param downloadStatus the new download status to update the asset message
     * @param conversationId the conversation identifier
     * @param messageId the message identifier
     * @return [UpdateDownloadStatusResult] sealed class with either a Success state in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(
        downloadStatus: DownloadStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateDownloadStatusResult
}

internal class UpdateAssetMessageDownloadStatusUseCaseImpl(
    private val messageRepository: MessageRepository
) : UpdateAssetMessageDownloadStatusUseCase {

    override suspend operator fun invoke(
        downloadStatus: DownloadStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateDownloadStatusResult {
        return messageRepository.updateAssetMessageDownloadStatus(downloadStatus, conversationId, messageId).fold({
            UpdateDownloadStatusResult.Failure(it)
        }, {
            UpdateDownloadStatusResult.Success
        })
    }
}

sealed class UpdateDownloadStatusResult {
    data object Success : UpdateDownloadStatusResult()
    data class Failure(val coreFailure: CoreFailure) : UpdateDownloadStatusResult()
}
