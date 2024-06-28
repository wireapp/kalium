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

package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import kotlinx.datetime.Clock

/**
 * Internal UseCase that updates Group Conversation Receipt Mode value
 * Possible values: [Conversation.ReceiptMode.ENABLED] and [Conversation.ReceiptMode.DISABLED]
 * Returns: [ConversationUpdateReceiptModeResult]
 */
interface UpdateConversationReceiptModeUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ): ConversationUpdateReceiptModeResult
}

internal class UpdateConversationReceiptModeUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId
) : UpdateConversationReceiptModeUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ): ConversationUpdateReceiptModeResult =
        conversationRepository.updateReceiptMode(
            conversationId = conversationId,
            receiptMode = receiptMode
        ).fold({
            ConversationUpdateReceiptModeResult.Failure(it)
        }, {
            handleSystemMessage(
                conversationId = conversationId,
                receiptMode = receiptMode
            )
            ConversationUpdateReceiptModeResult.Success
        })

    private suspend fun handleSystemMessage(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ) {
        val message = Message.System(
            uuid4().toString(),
            MessageContent.ConversationReceiptModeChanged(
                receiptMode = receiptMode == Conversation.ReceiptMode.ENABLED
            ),
            conversationId,
            Clock.System.now(),
            selfUserId,
            Message.Status.Sent,
            Message.Visibility.VISIBLE,
            expirationData = null
        )

        persistMessage(message)
    }
}

sealed interface ConversationUpdateReceiptModeResult {
    data object Success : ConversationUpdateReceiptModeResult
    data class Failure(val cause: CoreFailure) : ConversationUpdateReceiptModeResult
}
