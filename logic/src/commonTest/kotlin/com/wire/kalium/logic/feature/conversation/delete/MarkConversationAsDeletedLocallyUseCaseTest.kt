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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.coVerify
import io.mockative.eq
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class MarkConversationAsDeletedLocallyUseCaseTest {

    @Test
    fun givenSuccess_whenInvoking_thenSuccessResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement().arrange {
            withMarkConversationAsDeletedLocallySucceeding()
        }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<MarkConversationAsDeletedResult.Success>(result)
        coVerify { arrangement.conversationRepository.markConversationAsDeletedLocally(eq(CONVERSATION_ID)) }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenFailure_whenInvoking_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement().arrange {
            withMarkConversationAsDeletedLocallyFailing()
        }

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<MarkConversationAsDeletedResult.Failure>(result)
        coVerify { arrangement.conversationRepository.markConversationAsDeletedLocally(eq(CONVERSATION_ID)) }.wasInvoked(exactly = 1)
    }

    private class Arrangement : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {
        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, MarkConversationAsDeletedLocallyUseCase> = run {
            val useCase = MarkConversationAsDeletedLocallyUseCaseImpl(conversationRepository = conversationRepository)
            block()
            this to useCase
        }
    }

    companion object {
        val CONVERSATION_ID = ConversationId("someValue", "someDomain")
    }
}
