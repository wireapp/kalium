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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.IncomingReceiptPersistence
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.message.receipt.toMessageStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.ReadReceiptEventData

public interface ReceiptMessageHandler {
    public suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Receipt,
    )
}

public class ReceiptMessageHandlerImpl public constructor(
    private val selfUserId: UserId,
    private val incomingReceiptPersistence: IncomingReceiptPersistence,
    private val persistenceEventHookNotifier: PersistenceEventHookNotifier,
) : ReceiptMessageHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Receipt,
    ) {
        // Receipts from self user shouldn't happen,
        // If it happens, it's unnecessary,
        // and we can squish some performance by skipping it completely
        if (message.senderUserId == selfUserId) return

        incomingReceiptPersistence.updateReferencedMessageStatusesIfNotRead(
            messageStatus = messageContent.type.toMessageStatus(),
            messageIds = messageContent.messageIds,
            conversationId = message.conversationId,
        )

        incomingReceiptPersistence.insertReceipts(
            userId = message.senderUserId,
            conversationId = message.conversationId,
            date = message.date,
            type = messageContent.type,
            messageIds = messageContent.messageIds,
        )

        if (messageContent.type == ReceiptType.READ) {
            persistenceEventHookNotifier.onReadReceiptPersisted(
                ReadReceiptEventData(message.conversationId, messageContent.messageIds, message.date),
                selfUserId,
            )
        }
    }
}
