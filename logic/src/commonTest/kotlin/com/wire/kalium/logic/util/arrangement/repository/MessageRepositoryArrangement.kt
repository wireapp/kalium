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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

internal interface MessageRepositoryArrangement {
    @Mock
    val messageRepository: MessageRepository

    fun withGetMessageById(
        result: Either<StorageFailure, Message>,
        messageID: Matcher<String> = any(),
        conversationId: Matcher<ConversationId> = any()
    )

    fun withDeleteMessage(
        result: Either<CoreFailure, Unit>,
        messageID: Matcher<String> = any(),
        conversationId: Matcher<ConversationId> = any()
    )

    fun withMarkAsDeleted(
        result: Either<StorageFailure, Unit>,
        messageID: Matcher<String> = any(),
        conversationId: Matcher<ConversationId> = any()
    )

    fun withLocalNotifications(list: Flow<Either<CoreFailure, List<LocalNotification>>>)

    fun withMoveMessagesToAnotherConversation(
        result: Either<StorageFailure, Unit>,
        originalConversation: Matcher<ConversationId> = any(),
        targetConversation: Matcher<ConversationId> = any()
    )
}

internal open class MessageRepositoryArrangementImpl : MessageRepositoryArrangement {
    @Mock
    override val messageRepository: MessageRepository = mock(MessageRepository::class)

    override fun withGetMessageById(
        result: Either<StorageFailure, Message>,
        messageID: Matcher<String>,
        conversationId: Matcher<ConversationId>
    ) {
        given(messageRepository)
            .suspendFunction(messageRepository::getMessageById)
            .whenInvokedWith(conversationId, messageID)
            .thenReturn(result)
    }

    override fun withDeleteMessage(
        result: Either<CoreFailure, Unit>,
        messageID: Matcher<String>,
        conversationId: Matcher<ConversationId>
    ) {
        given(messageRepository)
            .suspendFunction(messageRepository::deleteMessage)
            .whenInvokedWith(messageID, conversationId)
            .thenReturn(result)
    }

    override fun withMarkAsDeleted(
        result: Either<StorageFailure, Unit>,
        messageID: Matcher<String>,
        conversationId: Matcher<ConversationId>
    ) {
        given(messageRepository)
            .suspendFunction(messageRepository::markMessageAsDeleted)
            .whenInvokedWith(messageID, conversationId)
            .thenReturn(result)
    }

    override fun withLocalNotifications(list: Flow<Either<CoreFailure, List<LocalNotification>>>) {
        given(messageRepository)
            .suspendFunction(messageRepository::getNotificationMessage)
            .whenInvokedWith(any())
            .thenReturn(list)
    }

    override fun withMoveMessagesToAnotherConversation(
        result: Either<StorageFailure, Unit>,
        originalConversation: Matcher<ConversationId>,
        targetConversation: Matcher<ConversationId>
    ) {
        given(messageRepository)
            .suspendFunction(messageRepository::moveMessagesToAnotherConversation)
            .whenInvokedWith(originalConversation, targetConversation)
            .thenReturn(result)
    }
}
