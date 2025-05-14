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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.withContext

@Mockable
interface UpdateAssetMessageTransferStatusUseCase {
    /**
     * Function that allows update an asset message transfer status.
     *
     * @param transferStatus the new transfer status to update the asset message
     * @param conversationId the conversation identifier
     * @param messageId the message identifier
     * @return [UpdateTransferStatusResult] sealed class with either a Success state in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(
        transferStatus: AssetTransferStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateTransferStatusResult
}

internal class UpdateAssetMessageTransferStatusUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : UpdateAssetMessageTransferStatusUseCase {

    override suspend operator fun invoke(
        transferStatus: AssetTransferStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateTransferStatusResult = withContext(dispatcher.io) {
        messageRepository.updateAssetMessageTransferStatus(transferStatus, conversationId, messageId).fold({
            UpdateTransferStatusResult.Failure(it)
        }, {
            UpdateTransferStatusResult.Success
        })
    }
}

sealed class UpdateTransferStatusResult {
    data object Success : UpdateTransferStatusResult()
    data class Failure(val coreFailure: CoreFailure) : UpdateTransferStatusResult()
}
