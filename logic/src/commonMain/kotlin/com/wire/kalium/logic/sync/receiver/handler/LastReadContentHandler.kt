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

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import io.mockative.Mockable

@Mockable
internal interface LastReadContentHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.LastRead
    )
}

// This class handles the messages that arrive when some client has read the conversation.
internal class LastReadContentHandlerImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase,
    private val notificationEventsManager: NotificationEventsManager
) : LastReadContentHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.LastRead
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == selfUserId
        val isMessageDestinedForSelfConversation: Boolean = isMessageSentInSelfConversation(message)

        if (isMessageComingFromOtherClient && isMessageDestinedForSelfConversation) {
            // If the message is coming from other client, it means that the user has read
            // the conversation on the other device, and we can update the read date locally
            // to synchronize the state across the clients.
            conversationRepository.updateConversationReadDate(
                qualifiedID = messageContent.conversationId,
                date = messageContent.time
            ).flatMap { conversationRepository.getConversationUnreadEventsCount(messageContent.conversationId) }
                .map {
                    if (it <= 0)
                        notificationEventsManager.scheduleConversationSeenNotification(messageContent.conversationId)
                }
        }
    }

}
