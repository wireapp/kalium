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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.notification.EphemeralConversationNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import kotlinx.coroutines.flow.firstOrNull

public interface DeletedConversationEventHandler {
    public suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.DeletedConversation)
}

@Suppress("LongParameterList")
public class DeletedConversationEventHandlerImpl public constructor(
    private val userRepository: ConversationEventUserRepository,
    private val conversationRepository: DeletedConversationEventRepository,
    private val notificationEventsManager: NotificationEventsManager,
    private val deleteConversation: suspend (
        CryptoTransactionContext,
        ConversationId,
    ) -> Either<CoreFailure, Unit>,
    private val persistenceEventHookNotifier: PersistenceEventHookNotifier,
    private val selfUserId: UserId,
) : DeletedConversationEventHandler {

    override suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.DeletedConversation) {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        conversationRepository.getConversationById(event.conversationId)
            .onFailure {
                logger.logComplete(
                    EventLoggingStatus.SKIPPED,
                    arrayOf(
                        "info" to "Conversation delete event already handled?. Couldn't find the conversation."
                    )
                )
            }
            .flatMap { conversation ->
                deleteConversation(transactionContext, event.conversationId)
                    .onFailure {
                        logger.logFailure(it)
                    }.onSuccess {
                        val senderUser = userRepository.observeUser(event.senderUserId).firstOrNull()
                        val dataNotification = EphemeralConversationNotification(event, conversation, senderUser)
                        if (conversation.type != Conversation.Type.Group.Meeting) {
                            notificationEventsManager.scheduleDeleteConversationNotification(dataNotification)
                        }
                        logger.logSuccess()
                    }
            }
        persistenceEventHookNotifier.onConversationDeleted(
            ConversationDeleteEventData(event.conversationId),
            selfUserId
        )
    }
}
