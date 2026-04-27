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
package com.wire.kalium.cells.domain.usecase

import app.cash.paging.PagingData
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.model.CellConversation
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class GetCellConversationsPagedUseCaseTest {

    private companion object {
        val CONVERSATION_1 = CellConversation(
            id = ConversationId("conv1", "wire.com"),
            name = "Engineering",
            isChannel = false,
            channelAccess = null,
        )
    }

    @Test
    fun givenRepository_whenInvoked_thenReturnsNonNullFlow() = runTest {
        val (_, useCase) = Arrangement()
            .withPaginatedConversations(listOf(CONVERSATION_1))
            .arrange()

        val flow: Flow<PagingData<CellConversation>> = useCase()

        assertNotNull(flow)
    }

    @Test
    fun givenEmptyRepository_whenInvoked_thenReturnsNonNullFlow() = runTest {
        val (_, useCase) = Arrangement()
            .withPaginatedConversations(emptyList())
            .arrange()

        val flow: Flow<PagingData<CellConversation>> = useCase()

        assertNotNull(flow)
    }

    @Test
    fun givenRepository_whenInvokedTwice_thenReturnsSeparateFlowInstances() = runTest {
        val (_, useCase) = Arrangement()
            .withPaginatedConversations(emptyList())
            .arrange()

        val flow1 = useCase()
        val flow2 = useCase()

        assertNotNull(flow1)
        assertNotNull(flow2)
        // Each invocation must create a fresh Pager — they are never the same instance
        assertNotSame(flow1, flow2)
    }

    private class Arrangement {
        val repository: CellConversationRepository = mock(CellConversationRepository::class)

        suspend fun withPaginatedConversations(conversations: List<CellConversation>) = apply {
            coEvery {
                repository.getPaginatedCellGroupConversations(any(), any(), any())
            }.returns(conversations.right())
        }

        fun arrange(): Pair<Arrangement, GetCellConversationsPagedUseCase> {
            return this to GetCellConversationsPagedUseCaseImpl(repository)
        }
    }
}
