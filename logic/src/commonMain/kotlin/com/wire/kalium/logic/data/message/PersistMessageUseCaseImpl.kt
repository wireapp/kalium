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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.NoOpPersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.persistence.dao.message.InsertMessageResult

/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */

internal class PersistMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val selfUserId: UserId,
    private val notificationEventsManager: NotificationEventsManager,
    private val persistMessageHookNotifier: PersistenceEventHookNotifier = NoOpPersistenceEventHookNotifier
) : PersistMessageUseCase {
    override suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
        val modifiedMessage = getExpectsReadConfirmationFromMessage(message)
        val isSelfSender = message.isSelfTheSender(selfUserId)

        return messageRepository.persistMessage(
            message = modifiedMessage,
            updateConversationModifiedDate = message.content.shouldUpdateConversationOrder()
        ).onSuccess {
            val isConversationMuted = it == InsertMessageResult.INSERTED_INTO_MUTED_CONVERSATION

            if (!isConversationMuted && !isSelfSender && message.content.shouldNotifyUser()) {
                notificationEventsManager.scheduleRegularNotificationChecking()
            }

            persistMessageHookNotifier.onMessagePersisted(
                modifiedMessage.toPersistedMessageData(),
                selfUserId
            )
        }.map { }
    }

    private fun Message.Standalone.toPersistedMessageData() = PersistedMessageData(
        conversationId = conversationId,
        messageId = id,
        content = content,
        date = date,
        expireAfter = expirationData?.expireAfter
    )

    private fun Message.isSelfTheSender(selfUserId: UserId) = senderUserId == selfUserId

    private suspend fun getExpectsReadConfirmationFromMessage(message: Message.Standalone) =
        if (message is Message.Regular) {
            messageRepository
                .getReceiptModeFromGroupConversationByQualifiedID(message.conversationId)
                .fold({ message }, { message.withGroupReceiptMode(it) })
        } else {
            message
        }

}
