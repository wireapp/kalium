/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.createEventProcessingLogger

internal interface DeleteConversationReminderEventHandler {
    suspend fun handle(event: Event.Conversation.AdminlessDeleteReminder)
}

internal class DeleteConversationReminderEventHandlerImpl(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
) : DeleteConversationReminderEventHandler {

    override suspend fun handle(event: Event.Conversation.AdminlessDeleteReminder) {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        persistMessage(
            Message.System(
                id = event.id,
                content = MessageContent.AdminlessDeleteReminder(event.deletionScheduledFor),
                conversationId = event.conversationId,
                date = event.dateTime,
                senderUserId = event.senderUserId ?: selfUserId,
                status = Message.Status.Sent,
                visibility = Message.Visibility.VISIBLE,
                expirationData = null,
            )
        )
            .onSuccess { logger.logSuccess() }
            .onFailure(logger::logFailure)
    }
}
