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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.datetime.Instant

interface ReceiptMessageHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Receipt
    )
}

internal class ReceiptMessageHandlerImpl(
    private val selfUserId: UserId,
    private val receiptRepository: ReceiptRepository,
    private val messageRepository: MessageRepository
) : ReceiptMessageHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Receipt
    ) {
        // Receipts from self user shouldn't happen,
        // If it happens, it's unnecessary,
        // and we can squish some performance by skipping it completely
        if (message.senderUserId == selfUserId) return

        updateMessageStatus(messageContent, message)

        receiptRepository.persistReceipts(
            userId = message.senderUserId,
            conversationId = message.conversationId,
            date = Instant.parse(message.date),
            type = messageContent.type,
            messageIds = messageContent.messageIds
        )
    }

    private suspend fun updateMessageStatus(
        messageContent: MessageContent.Receipt,
        message: Message.Signaling
    ) {
        messageContent.messageIds.forEach {
            messageRepository.updateMessageStatus(
                messageStatus = when (messageContent.type) {
                    ReceiptType.DELIVERED -> MessageEntity.Status.DELIVERED
                    ReceiptType.READ -> MessageEntity.Status.READ
                },
                conversationId = message.conversationId,
                messageUuid = it
            )
        }
    }

}
