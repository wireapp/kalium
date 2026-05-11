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
package com.wire.kalium.cells.domain.paging

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadParamsRefresh
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultPage
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.model.CellConversation
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ConversationPagingSourceTest {

    private companion object {
        const val PAGE_SIZE = 3
        val CONVERSATION_1 = CellConversation(
            id = ConversationId("conv1", "wire.com"),
            name = "Alpha",
            isChannel = false,
            channelAccess = null,
        )
        val CONVERSATION_2 = CellConversation(
            id = ConversationId("conv2", "wire.com"),
            name = "Beta",
            isChannel = false,
            channelAccess = null,
        )
        val CONVERSATION_3 = CellConversation(
            id = ConversationId("conv3", "wire.com"),
            name = "Gamma",
            isChannel = false,
            channelAccess = null,
        )
    }

    @Test
    fun givenNullKey_whenLoad_thenUsesOffsetZero() = runTest {
        val (_, pagingSource) = Arrangement()
            .withPaginatedConversations(offset = 0, result = listOf(CONVERSATION_1))
            .arrange()

        val result = pagingSource.load(refreshParams(null, PAGE_SIZE))

        assertIs<PagingSourceLoadResultPage<Int, CellConversation>>(result)
        assertEquals(listOf(CONVERSATION_1), result.data)
    }

    @Test
    fun givenFullPage_whenLoad_thenNextKeyIsOffsetPlusPageSize() = runTest {
        val conversations = listOf(CONVERSATION_1, CONVERSATION_2, CONVERSATION_3)
        val (_, pagingSource) = Arrangement()
            .withPaginatedConversations(offset = 0, result = conversations)
            .arrange()

        val result = pagingSource.load(refreshParams(null, PAGE_SIZE))

        assertIs<PagingSourceLoadResultPage<Int, CellConversation>>(result)
        assertEquals(PAGE_SIZE, result.nextKey)
    }

    @Test
    fun givenPartialPage_whenLoad_thenNextKeyIsNull() = runTest {
        val (_, pagingSource) = Arrangement()
            .withPaginatedConversations(offset = 0, result = listOf(CONVERSATION_1, CONVERSATION_2))
            .arrange()

        val result = pagingSource.load(refreshParams(null, PAGE_SIZE))

        assertIs<PagingSourceLoadResultPage<Int, CellConversation>>(result)
        assertNull(result.nextKey)
    }

    @Test
    fun givenEmptyPage_whenLoad_thenNextKeyIsNull() = runTest {
        val (_, pagingSource) = Arrangement()
            .withPaginatedConversations(offset = 0, result = emptyList())
            .arrange()

        val result = pagingSource.load(refreshParams(null, PAGE_SIZE))

        assertIs<PagingSourceLoadResultPage<Int, CellConversation>>(result)
        assertNull(result.nextKey)
    }

    @Test
    fun givenNonZeroKey_whenLoad_thenPassesKeyAsOffset() = runTest {
        val conversations = listOf(CONVERSATION_3)
        val (_, pagingSource) = Arrangement()
            .withPaginatedConversations(offset = PAGE_SIZE, result = conversations)
            .arrange()

        val result = pagingSource.load(appendParams(PAGE_SIZE, PAGE_SIZE))

        assertIs<PagingSourceLoadResultPage<Int, CellConversation>>(result)
        assertEquals(listOf(CONVERSATION_3), result.data)
        assertNull(result.nextKey)
    }

    @Test
    fun givenFullPage_whenLoad_thenPrevKeyIsAlwaysNull() = runTest {
        val conversations = listOf(CONVERSATION_1, CONVERSATION_2, CONVERSATION_3)
        val (_, pagingSource) = Arrangement()
            .withPaginatedConversations(offset = 0, result = conversations)
            .arrange()

        val result = pagingSource.load(refreshParams(null, PAGE_SIZE))

        assertIs<PagingSourceLoadResultPage<Int, CellConversation>>(result)
        assertNull(result.prevKey)
    }

    @Test
    fun givenRepositoryFailure_whenLoad_thenReturnsError() = runTest {
        val (_, pagingSource) = Arrangement()
            .withPaginatedConversationsError(StorageFailure.DataNotFound)
            .arrange()

        val result = pagingSource.load(refreshParams(null, PAGE_SIZE))

        assertIs<PagingSourceLoadResultError<Int, CellConversation>>(result)
    }

    @Test
    fun givenPageSizeTwenty_whenLoad_thenRepositoryCalledWithLimit20() = runTest {
        val repo: CellConversationRepository = mock<CellConversationRepository>(mode = MockMode.autoUnit)
        everySuspend {
            repo.getPaginatedCellGroupConversations(20, 0, "")
        }.returns(listOf(CONVERSATION_1).right())

        val pagingSource = ConversationPagingSource(pageSize = 20, repository = repo)
        val result = pagingSource.load(refreshParams(null, 20))

        assertIs<PagingSourceLoadResultPage<Int, CellConversation>>(result)
        assertEquals(listOf(CONVERSATION_1), result.data)
    }

    @Test
    fun givenAnyState_whenGetRefreshKey_thenReturnsNull() = runTest {
        val (_, pagingSource) = Arrangement().arrange()

        val refreshKey = pagingSource.getRefreshKey(
            app.cash.paging.PagingState(
                pages = emptyList(),
                anchorPosition = null,
                config = app.cash.paging.PagingConfig(PAGE_SIZE),
                leadingPlaceholderCount = 0,
            )
        )

        assertNull(refreshKey)
    }

    private inner class Arrangement {
        val repository: CellConversationRepository = mock<CellConversationRepository>(mode = MockMode.autoUnit)

        suspend fun withPaginatedConversations(offset: Int, result: List<CellConversation>) = apply {
            everySuspend {
                repository.getPaginatedCellGroupConversations(PAGE_SIZE, offset, "")
            }.returns(result.right())
        }

        suspend fun withPaginatedConversationsError(failure: StorageFailure) = apply {
            everySuspend {
                repository.getPaginatedCellGroupConversations(any(), any(), any())
            }.returns(failure.left())
        }

        fun arrange(): Pair<Arrangement, ConversationPagingSource> {
            return this to ConversationPagingSource(PAGE_SIZE, repository)
        }
    }
}

// Helpers — PagingSourceLoadParamsRefresh/Append are not declared as subtypes of
// PagingSourceLoadParams in the KMP common expect declarations; the relationship
// only exists in the JVM/Android actual typealiases. The casts are safe at runtime.
@Suppress("UNCHECKED_CAST")
private fun refreshParams(key: Int?, loadSize: Int): app.cash.paging.PagingSourceLoadParams<Int> =
    PagingSourceLoadParamsRefresh<Int>(key, loadSize, false) as app.cash.paging.PagingSourceLoadParams<Int>

@Suppress("UNCHECKED_CAST")
private fun appendParams(key: Int, loadSize: Int): app.cash.paging.PagingSourceLoadParams<Int> =
    PagingSourceLoadParamsAppend<Int>(key, loadSize, false) as app.cash.paging.PagingSourceLoadParams<Int>
