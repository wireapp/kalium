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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.notification.EphemeralConversationNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import com.wire.kalium.logic.util.EventLoggingStatus
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import kotlinx.coroutines.flow.firstOrNull

internal interface DeletedConversationEventHandler {
    suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.DeletedConversation
    ): Either<CoreFailure, Unit>
}

internal class DeletedConversationEventHandlerImpl(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val notificationEventsManager: NotificationEventsManager,
    private val deleteConversation: DeleteConversationUseCase,
    private val persistenceEventHookNotifier: PersistenceEventHookNotifier,
    private val selfUserId: UserId,
) : DeletedConversationEventHandler {

    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.DeletedConversation
    ): Either<CoreFailure, Unit> {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        val result: Either<CoreFailure, Unit> = conversationRepository.getConversationById(event.conversationId)
            .fold(
                { failure ->
                    if (failure is StorageFailure.DataNotFound) {
                        logger.logComplete(
                            EventLoggingStatus.SKIPPED,
                            arrayOf("info" to "Conversation delete event was already handled")
                        )
                        Either.Right(Unit)
                    } else {
                        logger.logFailure(failure)
                        Either.Left(failure)
                    }
                },
                { conversation ->
                    deleteConversation(transactionContext, event.conversationId)
                        .onFailure {
                            logger.logFailure(it)
                        }.onSuccess {
                            val senderUser = userRepository.observeUser(event.senderUserId).firstOrNull()
                            val dataNotification = EphemeralConversationNotification(event, conversation, senderUser)
                            notificationEventsManager.scheduleDeleteConversationNotification(dataNotification)
                            logger.logSuccess()
                        }
                }
            )
        persistenceEventHookNotifier.onConversationDeleted(
            ConversationDeleteEventData(event.conversationId),
            selfUserId
        )
        return result
    }
}
