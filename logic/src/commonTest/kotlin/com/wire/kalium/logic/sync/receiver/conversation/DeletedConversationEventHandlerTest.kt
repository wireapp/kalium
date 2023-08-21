/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.EphemeralEventsNotificationManager
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeletedConversationEventHandlerTest {

    @Test
    fun givenADeletedConversationEvent_whenHandlingItAndNotExists_thenShouldSkipTheDeletion() = runTest {
        val event = TestEvent.deletedConversation()
        val (arrangement, eventHandler) = Arrangement()
            .withEphemeralNotificationEnqueue()
            .withGetConversation(null)
            .withGetUserAuthor(event.senderUserId)
            .withDeletingConversationSucceeding()
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::deleteConversation)
                .with(eq(TestConversation.ID))
                .wasNotInvoked()
        }
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingIt_thenShouldDeleteTheConversationAndItsContent() = runTest {
        val event = TestEvent.deletedConversation()
        val (arrangement, eventHandler) = Arrangement()
            .withEphemeralNotificationEnqueue()
            .withGetConversation()
            .withGetUserAuthor(event.senderUserId)
            .withDeletingConversationSucceeding()
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::deleteConversation)
                .with(eq(TestConversation.ID))
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        private val ephemeralNotifications = mock(classOf<EphemeralEventsNotificationManager>())

        private val deletedConversationEventHandler: DeletedConversationEventHandler = DeletedConversationEventHandlerImpl(
            userRepository,
            conversationRepository,
            ephemeralNotifications
        )

        fun withDeletingConversationSucceeding(conversationId: ConversationId = TestConversation.ID) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::deleteConversation)
                .whenInvokedWith((eq(conversationId)))
                .thenReturn(Either.Right(Unit))
        }

        fun withGetConversation(conversation: Conversation? = TestConversation.CONVERSATION) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationById)
                .whenInvokedWith(any())
                .thenReturn(conversation)
        }

        fun withGetUserAuthor(userId: UserId = TestUser.USER_ID) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeUser)
                .whenInvokedWith(eq(userId))
                .thenReturn(flowOf(TestUser.OTHER))
        }

        fun withEphemeralNotificationEnqueue() = apply {
            given(ephemeralNotifications)
                .suspendFunction(ephemeralNotifications::scheduleDeleteConversationNotification)
                .whenInvokedWith(any())
                .thenDoNothing()
        }

        fun arrange() = this to deletedConversationEventHandler
    }

}
