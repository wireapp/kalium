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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class MarkConversationAsReadLocallyUseCaseTest {

    @Test
    fun givenRepositorySuccess_whenMarkingLocally_thenPersistReadDateAndReturnUnreadEventsState() = runTest {
        val conversationId = TestConversation.CONVERSATION.id
        val newLastRead = Clock.System.now() + 1.seconds
        val conversationRepository = mock<ConversationRepository>()
        val useCase = MarkConversationAsReadLocallyUseCaseImpl(conversationRepository = conversationRepository)

        everySuspend {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(conversationId, newLastRead)
        } returns Either.Right(true)

        val result = useCase(conversationId, newLastRead)

        assertEquals(MarkConversationAsReadResult.Success(hasUnreadEvents = true), result)
        verifySuspend(VerifyMode.exactly(1)) {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(conversationId, newLastRead)
        }
    }

    @Test
    fun givenRepositoryFailure_whenMarkingLocally_thenReturnFailure() = runTest {
        val conversationId = TestConversation.CONVERSATION.id
        val newLastRead = Clock.System.now()
        val conversationRepository = mock<ConversationRepository>()
        val useCase = MarkConversationAsReadLocallyUseCaseImpl(conversationRepository = conversationRepository)

        everySuspend {
            conversationRepository.updateReadDateAndGetHasUnreadEvents(conversationId, newLastRead)
        } returns Either.Left(StorageFailure.DataNotFound)

        val result = useCase(conversationId, newLastRead)

        assertEquals(MarkConversationAsReadResult.Failure(StorageFailure.DataNotFound), result)
    }
}
