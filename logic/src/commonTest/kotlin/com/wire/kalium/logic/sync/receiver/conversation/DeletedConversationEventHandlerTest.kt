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

import com.wire.kalium.logic.feature.message.EphemeralConversationNotification
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.NotificationEventsManagerArrangement
import com.wire.kalium.logic.util.arrangement.usecase.EphemeralEventsNotificationManagerArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matchers.EqualsMatcher
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeletedConversationEventHandlerTest {

    @Test
    fun givenADeletedConversationEvent_whenHandlingItAndNotExists_thenShouldSkipTheDeletion() = runTest {
        val event = TestEvent.deletedConversation()
        val (arrangement, eventHandler) = arrange {
            withGetConversation(null)
            withObserveUser(userId = EqualsMatcher(event.senderUserId))
            withDeletingConversationSucceeding(EqualsMatcher(TestConversation.ID))
        }

        eventHandler.handle(event)

        with(arrangement) {
            coVerify {
                conversationRepository.deleteConversation(eq(TestConversation.ID))
            }.wasNotInvoked()
        }
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingIt_thenShouldDeleteTheConversationAndItsContent() = runTest {
        val event = TestEvent.deletedConversation()
        val conversation = TestConversation.CONVERSATION
        val otherUser = TestUser.OTHER
        val (arrangement, eventHandler) = arrange {
            withGetConversation(conversation)
            withObserveUser(flowOf(otherUser), EqualsMatcher(event.senderUserId))
            withDeletingConversationSucceeding(EqualsMatcher(TestConversation.ID))
        }

        eventHandler.handle(event)

        with(arrangement) {
            coVerify {
                conversationRepository.deleteConversation(eq(TestConversation.ID))
            }.wasInvoked(exactly = once)

            coVerify {
                notificationEventsManager.scheduleDeleteConversationNotification(
                    eq(
                        EphemeralConversationNotification(
                            event,
                            conversation,
                            otherUser
                        )
                    )
                )
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenADeletedConversationEvent_whenHandlingItWithError_thenNoSchedulingTheNotification() = runTest {
        val event = TestEvent.deletedConversation()
        val conversation = TestConversation.CONVERSATION
        val otherUser = TestUser.OTHER
        val (arrangement, eventHandler) = arrange {
            withGetConversation(conversation)
            withObserveUser(flowOf(otherUser), EqualsMatcher(event.senderUserId))
            withDeletingConversationFailing()
        }

        eventHandler.handle(event)

        with(arrangement) {
            coVerify {
                notificationEventsManager.scheduleDeleteConversationNotification(any())
            }.wasNotInvoked()
        }
    }

    private fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        NotificationEventsManagerArrangement by EphemeralEventsNotificationManagerArrangementImpl() {

        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to DeletedConversationEventHandlerImpl(
                conversationRepository = conversationRepository,
                userRepository = userRepository,
                notificationEventsManager = notificationEventsManager
            )
        }
    }

}
