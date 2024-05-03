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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.receipt.DetailedReceipt
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe the receipts on a message
 *
 * @param receiptRepository ReceiptRepository for observing the selected message receipts
 *
 * @return Flow<List<DetailedReceipt>> - Flow of DetailedReceipt List that should be shown to the user.
 * That Flow emits everytime a receipt on the message is added.
 */
interface ObserveMessageReceiptsUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        type: ReceiptType
    ): Flow<List<DetailedReceipt>>
}

internal class ObserveMessageReceiptsUseCaseImpl(
    private val receiptRepository: ReceiptRepository
) : ObserveMessageReceiptsUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String,
        type: ReceiptType
    ): Flow<List<DetailedReceipt>> =
        receiptRepository.observeMessageReceipts(
            conversationId = conversationId,
            messageId = messageId,
            type
        ).also {
            kaliumLogger.i("[ObserveMessageReceiptsUseCase] - Observing read receipts for " +
                    "Conversation: ${conversationId.toLogString()}")
        }
}
