/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DeleteConversationReminderEventHandlerTest {
    @Test
    fun reminderStoresTheTimestampAndContainsStorageFailures() = runTest {
        val event = TestEvent.adminlessDeleteReminder()
        for (fails in listOf(false, true)) {
            val dao = mock<ConversationDAO>()
            val insert = everySuspend { dao.insertAdminlessGroupDelete(event.conversationId.toDao(), event.deletionScheduledFor) }
            if (fails) insert throws IllegalStateException("storage failure") else insert returns Unit

            DeleteConversationReminderEventHandlerImpl(dao).handle(event)

            verifySuspend(VerifyMode.exactly(1)) {
                dao.insertAdminlessGroupDelete(eq(event.conversationId.toDao()), eq(event.deletionScheduledFor))
            }
        }
    }
}
