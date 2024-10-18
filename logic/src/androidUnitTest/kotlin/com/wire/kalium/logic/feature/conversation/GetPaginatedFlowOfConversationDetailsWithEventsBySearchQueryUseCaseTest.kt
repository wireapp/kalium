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

import androidx.paging.PagingData
import app.cash.paging.PagingConfig
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationQueryConfig
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationRepositoryExtensions
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCaseTest {
    private val dispatcher = TestKaliumDispatcher

    @Test
    fun givenSearchQuery_whenGettingPaginatedList_thenCallUseCaseWithProperParams() = runTest(dispatcher.default) {
        // Given
        val (arrangement, useCase) = Arrangement().withPaginatedConversationResult(emptyFlow()).arrange()
        with(arrangement) {
            // When
            useCase(queryConfig = queryConfig, pagingConfig = pagingConfig, startingOffset = startingOffset)
            // Then
            coVerify {
                conversationRepository.extensions
                    .getPaginatedConversationDetailsWithEventsBySearchQuery(queryConfig, pagingConfig, startingOffset)
            }.wasInvoked(exactly = once)
        }
    }

    inner class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val conversationRepositoryExtensions = mock(ConversationRepositoryExtensions::class)

        val queryConfig = ConversationQueryConfig("search")
        val pagingConfig = PagingConfig(20)
        val startingOffset = 0L

        init {
            every {
                conversationRepository.extensions
            }.returns(conversationRepositoryExtensions)
        }

        suspend fun withPaginatedConversationResult(result: Flow<PagingData<ConversationDetailsWithEvents>>) = apply {
            coEvery {
                conversationRepositoryExtensions.getPaginatedConversationDetailsWithEventsBySearchQuery(any(), any(), any())
            }.returns(result)
        }

        fun arrange() = this to GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase(dispatcher, conversationRepository)
    }
}
