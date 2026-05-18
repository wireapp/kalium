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
package com.wire.kalium.logic.feature.conversation

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationQueryConfig
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationRepositoryExtensions
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

internal class GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCaseTest {
    private val dispatcher = TestKaliumDispatcher

    @Test
    fun givenSearchQuery_whenGettingPaginatedList_thenCallUseCaseWithProperParams() = runTest(dispatcher.default) {
        // Given
        val (arrangement, useCase) = Arrangement().withPaginatedConversationResult(flowOf(PagingData.empty())).arrange()
        with(arrangement) {
            // When
            useCase(queryConfig = queryConfig, pagingConfig = pagingConfig, startingOffset = startingOffset, strictMlsFilter = false).first()
            // Then
            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.extensions
                    .getPaginatedConversationDetailsWithEventsBySearchQuery(queryConfig, emptyList(), pagingConfig, startingOffset, false)
            }
        }
    }

    inner class Arrangement {
        val conversationRepository = mock<ConversationRepository>()
        val conversationRepositoryExtensions = mock<ConversationRepositoryExtensions>()
        val callRepository = mock<CallRepository>()

        val queryConfig = ConversationQueryConfig("search")
        val pagingConfig = PagingConfig(20)
        val startingOffset = 0L

        init {
            every {
                conversationRepository.extensions
            }.returns(conversationRepositoryExtensions)
            everySuspend {
                callRepository.ongoingCallsFlow()
            } returns flowOf(emptyList())
        }

        suspend fun withPaginatedConversationResult(result: Flow<PagingData<ConversationDetailsWithEvents>>) = apply {
            everySuspend {
                conversationRepositoryExtensions.getPaginatedConversationDetailsWithEventsBySearchQuery(any(), any(), any(), any(), any())
            } returns result
        }

        fun arrange() = this to GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase(
            dispatcher,
            conversationRepository,
            callRepository
        )
    }
}
