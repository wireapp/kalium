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

package com.wire.kalium.logic.data.conversation

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingSource
import app.cash.paging.PagingState
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions
import com.wire.kalium.persistence.dao.message.KaliumPager
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ConversationRepositoryExtensionsTest {
    private val fakePagingSource = object : PagingSource<Int, ConversationDetailsWithEventsEntity>() {
        override fun getRefreshKey(state: PagingState<Int, ConversationDetailsWithEventsEntity>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ConversationDetailsWithEventsEntity> =
            LoadResult.Error(NotImplementedError("STUB for tests. Not implemented."))
    }

    @Test
    fun givenParameters_whenPaginatedConversationDetailsWithEvents_thenShouldCallDaoExtensionsWithRightParameters() = runTest {
        val pagingConfig = PagingConfig(20)
        val pager = Pager(pagingConfig) { fakePagingSource }
        val kaliumPager = KaliumPager(pager, fakePagingSource, StandardTestDispatcher())
        val (arrangement, conversationRepositoryExtensions) = Arrangement()
            .withConversationExtensionsReturningPager(kaliumPager)
            .arrange()
        val searchQuery = "search"
        val fromArchive = false
        conversationRepositoryExtensions.getPaginatedConversationDetailsWithEventsBySearchQuery(
            searchQuery = searchQuery,
            fromArchive = fromArchive,
            pagingConfig = pagingConfig,
            startingOffset = 0L
        )
        verify {
            arrangement.conversationDaoExtensions
                .getPagerForConversationDetailsWithEventsSearch(eq(searchQuery), eq(fromArchive), eq(pagingConfig), any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationDaoExtensions: ConversationExtensions = mock(ConversationExtensions::class)
        @Mock
        private val conversationDAO: ConversationDAO = mock(ConversationDAO::class)
        @Mock
        private val conversationMapper: ConversationMapper = mock(ConversationMapper::class)
        @Mock
        private val messageMapper: MessageMapper = mock(MessageMapper::class)
        private val conversationRepositoryExtensions: ConversationRepositoryExtensions by lazy {
            ConversationRepositoryExtensionsImpl(conversationDAO, conversationMapper, messageMapper)
        }

        init {
            every {
                messageMapper.fromEntityToMessage(any())
            }.returns(TestMessage.TEXT_MESSAGE)
            every {
                conversationMapper.fromDaoModelToDetails(any())
            }.returns(TestConversationDetails.CONVERSATION_GROUP)
            every {
                conversationDAO.platformExtensions
            }.returns(conversationDaoExtensions)
        }
        fun withConversationExtensionsReturningPager(kaliumPager: KaliumPager<ConversationDetailsWithEventsEntity>) = apply {
            every {
                conversationDaoExtensions.getPagerForConversationDetailsWithEventsSearch(any(), any(), any(), any())
            }.returns(kaliumPager)
        }
        fun arrange() = this to conversationRepositoryExtensions
    }
}
