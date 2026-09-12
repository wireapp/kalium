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

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
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
    fun givenReminderEvent_whenHandling_thenStoresConversationDeletionTimestamp() = runTest {
        val event = TestEvent.adminlessDeleteReminder()
        val (arrangement, handler) = Arrangement().arrange()

        handler.handle(event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationDAO.insertAdminlessGroupDelete(
                eq(event.conversationId.toDao()),
                eq(event.deletionScheduledFor),
            )
        }
    }

    @Test
    fun givenStorageFailure_whenHandlingReminder_thenFailureIsContained() = runTest {
        val event = TestEvent.adminlessDeleteReminder()
        val (_, handler) = Arrangement()
            .withInsertFailure(IllegalStateException("storage failure"))
            .arrange()

        handler.handle(event)
    }

    private class Arrangement {
        val conversationDAO = mock<ConversationDAO>()

        init {
            everySuspend {
                conversationDAO.insertAdminlessGroupDelete(any(), any())
            } returns Unit
        }

        fun withInsertFailure(throwable: Throwable) = apply {
            everySuspend {
                conversationDAO.insertAdminlessGroupDelete(any(), any())
            } throws throwable
        }

        fun arrange() = this to DeleteConversationReminderEventHandlerImpl(conversationDAO)
    }
}
