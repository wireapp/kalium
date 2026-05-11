/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.delete

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class MarkConversationAsDeletedLocallyUseCaseTest {

    @Test
    fun givenSuccess_whenInvoking_thenSuccessResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement().arrange {
            withSetConversationDeletedLocallySucceeding()
        }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<MarkConversationAsDeletedResult.Success>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setConversationDeletedLocally(eq(CONVERSATION_ID), eq(true))
        }
    }

    @Test
    fun givenFailure_whenInvoking_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement().arrange {
            withSetConversationDeletedLocallyFailing()
        }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<MarkConversationAsDeletedResult.Failure>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.setConversationDeletedLocally(eq(CONVERSATION_ID), eq(true))
        }
    }

    private class Arrangement {
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, MarkConversationAsDeletedLocallyUseCase> = run {
            val useCase = MarkConversationAsDeletedLocallyUseCaseImpl(conversationRepository = conversationRepository)
            block()
            this to useCase
        }

        suspend fun withSetConversationDeletedLocallySucceeding() {
            everySuspend { conversationRepository.setConversationDeletedLocally(any(), any()) } returns Either.Right(Unit)
        }

        suspend fun withSetConversationDeletedLocallyFailing() {
            everySuspend { conversationRepository.setConversationDeletedLocally(any(), any()) } returns Either.Left(CoreFailure.Unknown(null))
        }
    }

    companion object {
        val CONVERSATION_ID = ConversationId("someValue", "someDomain")
    }
}
