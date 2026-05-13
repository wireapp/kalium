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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.notification.LocalNotification
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.datetime.Instant

internal interface MessageRepositoryArrangement {

    val messageRepository: MessageRepository
    val systemMessageInserter: SystemMessageInserter

    suspend fun withGetMessageById(
        result: Either<StorageFailure, Message>,
        messageID: (String) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true }
    )

    suspend fun withDeleteMessage(
        result: Either<CoreFailure, Unit>,
        messageID: (String) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true }
    )

    suspend fun withMarkAsDeleted(
        result: Either<StorageFailure, Unit>,
        messageID: (String) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true }
    )

    suspend fun withLocalNotifications(list: Either<CoreFailure, List<LocalNotification>>)

    suspend fun withMoveMessagesToAnotherConversation(
        result: Either<StorageFailure, Unit>,
        originalConversation: (ConversationId) -> Boolean = { true },
        targetConversation: (ConversationId) -> Boolean = { true }
    )

    suspend fun withEditCompositeMessage(
        result: Either<StorageFailure, Unit>,
        conversationId: (ConversationId) -> Boolean = { true },
        content: (MessageContent.CompositeEdited) -> Boolean = { true },
        messageId: (String) -> Boolean = { true },
        date: (Instant) -> Boolean = { true }
    ) {
        everySuspend {
            messageRepository.updateCompositeMessage(
                matches { conversationId(it) },
                matches { content(it) },
                matches { messageId(it) },
                matches { date(it) }
            )
        }.returns(result)
    }
}

internal open class MessageRepositoryArrangementImpl : MessageRepositoryArrangement {

    override val messageRepository: MessageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
    override val systemMessageInserter = mock<SystemMessageInserter>(mode = MockMode.autoUnit)

    override suspend fun withGetMessageById(
        result: Either<StorageFailure, Message>,
        messageID: (String) -> Boolean,
        conversationId: (ConversationId) -> Boolean
    ) {
        everySuspend {
            messageRepository.getMessageById(
                matches { conversationId(it) },
                matches { messageID(it) }
            )
        }.returns(result)
    }

    override suspend fun withDeleteMessage(
        result: Either<CoreFailure, Unit>,
        messageID: (String) -> Boolean,
        conversationId: (ConversationId) -> Boolean
    ) {
        everySuspend {
            messageRepository.deleteMessage(
                matches { messageID(it) },
                matches { conversationId(it) }
            )
        }.returns(result)
    }

    override suspend fun withMarkAsDeleted(
        result: Either<StorageFailure, Unit>,
        messageID: (String) -> Boolean,
        conversationId: (ConversationId) -> Boolean
    ) {
        everySuspend {
            messageRepository.markMessageAsDeleted(
                matches { messageID(it) },
                matches { conversationId(it) }
            )
        }.returns(result)
    }

    override suspend fun withLocalNotifications(list: Either<CoreFailure, List<LocalNotification>>) {
        everySuspend {
            messageRepository.getNotificationMessage(any())
        }.returns(list)
    }

    override suspend fun withMoveMessagesToAnotherConversation(
        result: Either<StorageFailure, Unit>,
        originalConversation: (ConversationId) -> Boolean,
        targetConversation: (ConversationId) -> Boolean
    ) {
        everySuspend {
            messageRepository.moveMessagesToAnotherConversation(
                matches { originalConversation(it) },
                matches { targetConversation(it) }
            )
        }.returns(result)
    }

    override suspend fun withEditCompositeMessage(
        result: Either<StorageFailure, Unit>,
        conversationId: (ConversationId) -> Boolean,
        content: (MessageContent.CompositeEdited) -> Boolean,
        messageId: (String) -> Boolean,
        date: (Instant) -> Boolean
    ) {
        everySuspend {
            messageRepository.updateCompositeMessage(
                matches { conversationId(it) },
                matches { content(it) },
                matches { messageId(it) },
                matches { date(it) }
            )
        }.returns(result)
    }
}
