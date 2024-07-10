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
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

internal interface MessageRepositoryArrangement {
    @Mock
    val messageRepository: MessageRepository

    suspend fun withGetMessageById(
        result: Either<StorageFailure, Message>,
        messageID: Matcher<String> = AnyMatcher(valueOf()),
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf())
    )

    suspend fun withDeleteMessage(
        result: Either<CoreFailure, Unit>,
        messageID: Matcher<String> = AnyMatcher(valueOf()),
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf())
    )

    suspend fun withMarkAsDeleted(
        result: Either<StorageFailure, Unit>,
        messageID: Matcher<String> = AnyMatcher(valueOf()),
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf())
    )

<<<<<<< HEAD
    suspend fun withLocalNotifications(list: Either<CoreFailure, Flow<List<LocalNotification>>>)
=======
    fun withLocalNotifications(list: Flow<Either<CoreFailure, List<LocalNotification>>>)
>>>>>>> 33eff5b65a (feat: Add isReplyAllowed field to notification entity [WPB-7425] (#2867))

    suspend fun withMoveMessagesToAnotherConversation(
        result: Either<StorageFailure, Unit>,
        originalConversation: Matcher<ConversationId> = AnyMatcher(valueOf()),
        targetConversation: Matcher<ConversationId> = AnyMatcher(valueOf())
    )
}

internal open class MessageRepositoryArrangementImpl : MessageRepositoryArrangement {
    @Mock
    override val messageRepository: MessageRepository = mock(MessageRepository::class)

    override suspend fun withGetMessageById(
        result: Either<StorageFailure, Message>,
        messageID: Matcher<String>,
        conversationId: Matcher<ConversationId>
    ) {
        coEvery {
            messageRepository.getMessageById(
                matches { conversationId.matches(it) },
                matches { messageID.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withDeleteMessage(
        result: Either<CoreFailure, Unit>,
        messageID: Matcher<String>,
        conversationId: Matcher<ConversationId>
    ) {
        coEvery {
            messageRepository.deleteMessage(
                matches { messageID.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withMarkAsDeleted(
        result: Either<StorageFailure, Unit>,
        messageID: Matcher<String>,
        conversationId: Matcher<ConversationId>
    ) {
        coEvery {
            messageRepository.markMessageAsDeleted(
                matches { messageID.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.returns(result)
    }

<<<<<<< HEAD
    override suspend fun withLocalNotifications(list: Either<CoreFailure, Flow<List<LocalNotification>>>) {
        coEvery {
            messageRepository.getNotificationMessage(any())
        }.returns(list)
=======
    override fun withLocalNotifications(list: Flow<Either<CoreFailure, List<LocalNotification>>>) {
        given(messageRepository)
            .suspendFunction(messageRepository::getNotificationMessage)
            .whenInvokedWith(any())
            .thenReturn(list)
>>>>>>> 33eff5b65a (feat: Add isReplyAllowed field to notification entity [WPB-7425] (#2867))
    }

    override suspend fun withMoveMessagesToAnotherConversation(
        result: Either<StorageFailure, Unit>,
        originalConversation: Matcher<ConversationId>,
        targetConversation: Matcher<ConversationId>
    ) {
        coEvery {
            messageRepository.moveMessagesToAnotherConversation(
                matches { originalConversation.matches(it) },
                matches { targetConversation.matches(it) }
            )
        }.returns(result)
    }
}
