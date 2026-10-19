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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess

internal interface MessageTextEditHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.TextEdited
    ): Either<CoreFailure, Unit>
}

internal class MessageTextEditHandlerImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val messageDAO: MessageDAO,
    private val notificationEventsManager: NotificationEventsManager,
) : MessageTextEditHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.TextEdited
    ) = messageRepository.getMessageById(message.conversationId, messageContent.editMessageId).flatMap { currentMessage ->

        editTextMessage(messageDAO, currentMessage, message, messageContent).onSuccess { notify ->
            if (notify) notificationEventsManager.scheduleEditMessageNotification(message, messageContent)
        }.map { }
    }
}
