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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.messaging.hooks.ConversationLastReadEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class MarkConversationAsReadLocallyUseCaseTest {

    @Test
    fun givenNewerTimestamp_whenMarkingLocally_thenHookIsTriggered() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            lastReadDate = Clock.System.now()
        )
        val newLastRead = conversation.lastReadDate + 1.seconds
        val conversationRepository = mock<ConversationRepository>()
        val hookNotifier = mock<PersistenceEventHookNotifier>(mode = MockMode.autoUnit)
        val useCase = MarkConversationAsReadLocallyUseCaseImpl(
            conversationRepository = conversationRepository,
            persistenceEventHookNotifier = hookNotifier,
            selfUserId = TestUser.SELF.id,
        )

        everySuspend { conversationRepository.getConversationById(eq(conversation.id)) } returns Either.Right(conversation)
        everySuspend {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(eq(conversation.id), eq(newLastRead))
        } returns Either.Right(false)

        useCase(conversation.id, newLastRead)

        verifySuspend(VerifyMode.exactly(1)) {
            hookNotifier.onConversationLastReadPersisted(
                eq(ConversationLastReadEventData(conversation.id, newLastRead)),
                eq(TestUser.SELF.id)
            )
        }
    }
}
