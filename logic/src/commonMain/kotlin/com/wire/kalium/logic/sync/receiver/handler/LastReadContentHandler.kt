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

import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.KaliumSyncException

internal interface LastReadContentHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.LastRead
    )

    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.MarkUnread
    )

    suspend fun flushPendingLastReads()
}

// This class handles the messages that arrive when some client has read the conversation.
internal class LastReadContentHandlerImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase,
    private val notificationEventsManager: NotificationEventsManager
) : LastReadContentHandler {

    private val logger = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)
    private val updates = LastReadUpdates(selfUserId)

    override suspend fun handle(message: Message.Signaling, messageContent: MessageContent.LastRead) {
        if (message.senderUserId != selfUserId || !isMessageSentInSelfConversation(message)) return
        val operationTime = messageContent.operationTime ?: message.date
        if (!isValidReadOperation(operationTime, message.date)) return
        conversationRepository.clearManualUnreadMarker(messageContent.conversationId, messageContent.time, operationTime).getOrFail {
            throw KaliumSyncException("Failed to clear manual unread marker", it)
        }
        updates.add(message, messageContent, isSelfConversation = true)
    }

    override suspend fun handle(message: Message.Signaling, messageContent: MessageContent.MarkUnread) {
        if (message.senderUserId != selfUserId || !isMessageSentInSelfConversation(message)) return
        if (!messageContent.isValidFor(message.date)) return
        conversationRepository.markConversationAsUnread(
            messageContent.conversationId,
            messageContent.unreadFrom,
            messageContent.operationTime
        ).getOrFail {
            throw KaliumSyncException("Failed to mark conversation as unread", it)
        }
    }

    override suspend fun flushPendingLastReads() {
        val pending = updates.snapshot()
        if (pending.isEmpty()) return
        logger.d("$TAG Flushing LastRead updates")

        conversationRepository
            .updateReadDatesAndGetHasUnreadEvents(pending)
            .onSuccess { hasUnreadByConversation ->
                updates.persisted(pending)
                hasUnreadByConversation.forEach { (conversationId, hasUnreadEvents) ->
                    if (!hasUnreadEvents) {
                        notificationEventsManager.scheduleConversationSeenNotification(conversationId)
                    }
                }
            }
            .onFailure {
                logger.w("$TAG Failed to flush LastRead for ${pending.size} conversation(s): $it")
            }
        logger.d("$TAG Flush finished")
    }

    private companion object {
        const val TAG = "[LastReadContentHandler]"
    }
}
