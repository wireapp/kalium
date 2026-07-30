/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeleteConversationReminderEventHandlerTest {

    @Test
    fun givenReminderEventWithSender_whenHandling_thenPersistsExpectedSystemMessage() = runTest {
        val event = TestEvent.adminlessDeleteReminder()
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(eq(expectedMessage(event, event.senderUserId!!)))
        }
    }

    @Test
    fun givenReminderEventWithoutSender_whenHandling_thenUsesSelfUserAsSender() = runTest {
        val event = TestEvent.adminlessDeleteReminder().copy(senderUserId = null)
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(eq(expectedMessage(event, TestUser.OTHER_USER_ID)))
        }
    }

    @Test
    fun givenSameReminderEventTwice_whenHandling_thenReusesEventIdForPersistence() = runTest {
        val event = TestEvent.adminlessDeleteReminder()
        val (arrangement, handler) = Arrangement().arrange()
        val expected = expectedMessage(event, event.senderUserId!!)

        handler.handle(event)
        handler.handle(event)

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.persistMessage.invoke(eq(expected))
        }
    }

    private fun expectedMessage(
        event: Event.Conversation.AdminlessDeleteReminder,
        senderUserId: UserId,
    ) = Message.System(
        id = event.id,
        content = MessageContent.AdminlessDeleteReminder(event.deletionScheduledFor),
        conversationId = event.conversationId,
        date = event.dateTime,
        senderUserId = senderUserId,
        status = Message.Status.Sent,
        visibility = Message.Visibility.VISIBLE,
        expirationData = null,
    )

    private class Arrangement {
        val persistMessage = mock<PersistMessageUseCase>()

        init {
            everySuspend { persistMessage.invoke(any()) } returns Either.Right(Unit)
        }

        fun arrange() = this to DeleteConversationReminderEventHandlerImpl(
            persistMessage = persistMessage,
            selfUserId = TestUser.OTHER_USER_ID,
        )
    }
}
