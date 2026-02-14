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
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

@Mockable
internal interface LastReadContentHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.LastRead
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
    private val pendingLastReadByConversation = mutableMapOf<ConversationId, Instant>()
    private val pendingLastReadMutex = Mutex()

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.LastRead
    ) {
        val isMessageComingFromOtherClient = message.senderUserId == selfUserId
        val isMessageDestinedForSelfConversation: Boolean = isMessageSentInSelfConversation(message)

        if (isMessageComingFromOtherClient && isMessageDestinedForSelfConversation) {
            pendingLastReadMutex.withLock {
                val currentPending = pendingLastReadByConversation[messageContent.conversationId]
                if (currentPending == null || messageContent.time > currentPending) {
                    pendingLastReadByConversation[messageContent.conversationId] = messageContent.time
                }
            }
        }
    }

    override suspend fun flushPendingLastReads() {
        val pending = pendingLastReadMutex.withLock {
            if (pendingLastReadByConversation.isEmpty()) {
                emptyMap()
            } else {
                pendingLastReadByConversation.toMap().also {
                    pendingLastReadByConversation.clear()
                }
            }
        }
        if (pending.isEmpty()) return
        logger.d("$TAG Flushing LastRead updates")

        pending.forEach { (conversationId, readAt) ->
            conversationRepository
                .updateReadDateAndGetHasUnreadEvents(
                    qualifiedID = conversationId,
                    date = readAt
                )
                .onSuccess { hasUnreadEvents ->
                    if (!hasUnreadEvents) {
                        notificationEventsManager.scheduleConversationSeenNotification(conversationId)
                    }
                }
                .onFailure {
                    logger.w("$TAG Failed to flush LastRead. conversationId=${conversationId.toLogString()}")
                }
        }
        logger.d("$TAG Flush finished")
    }

    private companion object {
        const val TAG = "[LastReadContentHandler]"
    }
}
