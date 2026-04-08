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

package com.wire.kalium.logic.message

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingSource
import app.cash.paging.PagingState
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageRepositoryExtensions
import com.wire.kalium.logic.data.message.MessageRepositoryExtensionsImpl
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageExtensions
import com.wire.kalium.persistence.db.ReadDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageRepositoryExtensionsTest {

    private val fakePagingSource = object : PagingSource<Int, MessageEntity>() {
        override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageEntity> =
            LoadResult.Error(NotImplementedError("STUB for tests. Not implemented."))
    }

    @Test
    fun givenParameters_whenPaginatedMessagesByConversation_thenShouldCallDaoExtensionsWithRightParameters() = runTest {
        val pagingConfig = PagingConfig(20)
        val pager = Pager(pagingConfig) { fakePagingSource }
        val startingOffset = 0L

        val kaliumPager = KaliumPager(pager, fakePagingSource, ReadDispatcher(StandardTestDispatcher()))
        val (arrangement, messageRepositoryExtensions) = Arrangement()
            .withMessageExtensionsReturningPager(kaliumPager)
            .arrange()

        val visibilities = listOf(Message.Visibility.VISIBLE)
        messageRepositoryExtensions.getPaginatedMessagesByConversationIdAndVisibility(
            TestConversation.ID,
            visibilities,
            pagingConfig,
            startingOffset
        )

        verify {
            arrangement.messageDaoExtensions.getPagerForConversation(
                eq(CONVERSATION_ID_ENTITY), matches {
                    val list = it.toList()
                    list.size == 1 && list[0] == MessageEntity.Visibility.VISIBLE
                }, eq(pagingConfig),
                eq(startingOffset)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNomadEnabled_whenFetchingOlderMessages_thenUsesDaoTimestamp() = runTest {
        val pagingConfig = PagingConfig(20)
        val pager = Pager(pagingConfig) { fakePagingSource }
        val kaliumPager = KaliumPager(pager, fakePagingSource, ReadDispatcher(StandardTestDispatcher()))
        val (arrangement, messageRepositoryExtensions) = Arrangement()
            .withMessageExtensionsReturningPager(kaliumPager)
            .withOldestTimestamp(1234L)
            .withPagingCoordinator()
            .arrange()

        messageRepositoryExtensions.fetchOlderNomadMessagesByConversationId(
            conversationId = TestConversation.ID,
            pageSize = 5,
        )

        coVerify {
            arrangement.pagingCoordinator.fetchOlderMessagesIfNeeded(
                conversationId = eq(TestConversation.ID),
                pageSize = eq(5),
                beforeTimestampMs = eq(1234L),
                onInvalidate = any()
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNomadDisabled_whenFetchingOlderMessages_thenDoesNotCallCoordinator() = runTest {
        val pagingConfig = PagingConfig(20)
        val pager = Pager(pagingConfig) { fakePagingSource }
        val kaliumPager = KaliumPager(pager, fakePagingSource, ReadDispatcher(StandardTestDispatcher()))
        val (arrangement, messageRepositoryExtensions) = Arrangement()
            .withMessageExtensionsReturningPager(kaliumPager)
            .withOldestTimestamp(1234L)
            .withoutPagingCoordinator()
            .arrange()

        messageRepositoryExtensions.fetchOlderNomadMessagesByConversationId(
            conversationId = TestConversation.ID,
            pageSize = 5,
        )

        coVerify { arrangement.pagingCoordinator.fetchOlderMessagesIfNeeded(any(), any(), any(), any()) }
            .wasInvoked(exactly = 0)
    }

    @Test
    fun givenPagingCoordinator_whenObservingNomadState_thenUsesCoordinatorFlow() = runTest {
        val expectedState = NomadMessagePagingStatus(isFetching = true, hasMore = true)
        val (arrangement, messageRepositoryExtensions) = Arrangement()
            .withPagingCoordinatorState(flowOf(expectedState))
            .arrange()

        val state = messageRepositoryExtensions.observeNomadMessagePagingState(TestConversation.ID).first()

        assertEquals(expectedState, state)
        verify { arrangement.pagingCoordinator.observePagingState(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNoPagingCoordinator_whenObservingNomadState_thenReturnsDefaults() = runTest {
        val (arrangement, messageRepositoryExtensions) = Arrangement()
            .withoutPagingCoordinator()
            .arrange()

        val state = messageRepositoryExtensions.observeNomadMessagePagingState(TestConversation.ID).first()

        assertEquals(NomadMessagePagingStatus(isFetching = false, hasMore = false), state)
        verify { arrangement.pagingCoordinator.observePagingState(any()) }.wasInvoked(exactly = 0)
    }

    private data class Arrangement(
        val messageDaoExtensions: MessageExtensions = mock(MessageExtensions::class),
        val messageDAO: MessageDAO = mock(MessageDAO::class),
        val messageMapper: MessageMapper = mock(MessageMapper::class),
        val pagingCoordinator: NomadMessagePagingCoordinator = mock(NomadMessagePagingCoordinator::class),
        val usePagingCoordinator: Boolean = false,
    ) {
        init {
            every { messageMapper.fromEntityToMessage(any()) }.returns(MESSAGE)
            every { messageDAO.platformExtensions }.returns(messageDaoExtensions)
        }

        fun withMessageExtensionsReturningPager(kaliumPager: KaliumPager<MessageEntity>): Arrangement =
            copy().also {
                every {
                    it.messageDaoExtensions.getPagerForConversation(any(), any(), any(), any())
                }.returns(kaliumPager)
            }

        suspend fun withOldestTimestamp(timestamp: Long?): Arrangement = copy().also {
            coEvery {
                it.messageDAO.getOldestVisibleMessageTimestampByConversationId(CONVERSATION_ID_ENTITY)
            }.returns(timestamp)
        }

        suspend fun withPagingCoordinator(): Arrangement = copy(usePagingCoordinator = true).also {
            coEvery {
                it.pagingCoordinator.fetchOlderMessagesIfNeeded(any(), any(), any(), any())
            }.returns(Unit)
        }

        fun withPagingCoordinatorState(flow: kotlinx.coroutines.flow.Flow<NomadMessagePagingStatus>): Arrangement =
            copy(usePagingCoordinator = true).also {
                every { it.pagingCoordinator.observePagingState(any()) }.returns(flow)
            }

        fun withoutPagingCoordinator(): Arrangement = copy(usePagingCoordinator = false)

        private val messageRepositoryExtensions: MessageRepositoryExtensions by lazy {
            MessageRepositoryExtensionsImpl(
                messageDAO,
                messageMapper,
                if (usePagingCoordinator) pagingCoordinator else null,
            )
        }

        fun arrange() = this to messageRepositoryExtensions
    }

    private companion object {
        val CONVERSATION_ID_ENTITY = TestConversation.ENTITY_ID
        val MESSAGE = TestMessage.TEXT_MESSAGE
    }
}
