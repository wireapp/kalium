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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.MessageRepositoryExtensions
import com.wire.kalium.logic.data.message.MessageRepositoryExtensionsImpl
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingCoordinator
import com.wire.kalium.logic.data.message.paging.NomadMessagePagingResult
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageExtensions
import com.wire.kalium.persistence.db.ReadDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
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

        verify(VerifyMode.exactly(1)) {
            arrangement.messageDaoExtensions.getPagerForConversation(
                eq(CONVERSATION_ID_ENTITY), matching {
                    val list = it.toList()
                    list.size == 1 && list[0] == MessageEntity.Visibility.VISIBLE
                }, eq(pagingConfig),
                eq(startingOffset)
            )
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pagingCoordinator.fetchOlderMessagesIfNeeded(
                conversationId = eq(TestConversation.ID),
                pageSize = eq(5),
                beforeTimestampMs = eq(1234L),
                onInvalidate = any()
            )
        }
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

        verifySuspend(VerifyMode.not) { arrangement.pagingCoordinator.fetchOlderMessagesIfNeeded(any(), any(), any(), any()) }
    }

    @Test
    fun givenPagingCoordinator_whenFetchingOlderMessages_thenReturnsCoordinatorResult() = runTest {
        val expectedResult = NomadMessagePagingResult(hasMore = true)
        val (arrangement, messageRepositoryExtensions) = Arrangement()
            .withPagingCoordinatorResult(expectedResult)
            .withOldestTimestamp(1234L)
            .arrange()

        val result = messageRepositoryExtensions.fetchOlderNomadMessagesByConversationId(
            conversationId = TestConversation.ID,
            pageSize = 5,
        )

        assertEquals(expectedResult, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.pagingCoordinator.fetchOlderMessagesIfNeeded(any(), any(), any(), any()) }
    }

    private data class Arrangement(
        val messageDaoExtensions: MessageExtensions = mock<MessageExtensions>(),
        val messageDAO: MessageDAO = mock<MessageDAO>(),
        val messageMapper: MessageMapper = mock<MessageMapper>(),
        val pagingCoordinator: NomadMessagePagingCoordinator = mock<NomadMessagePagingCoordinator>(),
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
            everySuspend {
                it.messageDAO.getOldestVisibleMessageTimestampByConversationId(CONVERSATION_ID_ENTITY)
            } returns timestamp
        }

        suspend fun withPagingCoordinator(): Arrangement = copy(usePagingCoordinator = true).also {
            everySuspend {
                it.pagingCoordinator.fetchOlderMessagesIfNeeded(any(), any(), any(), any())
            } returns NomadMessagePagingResult(hasMore = true)
        }

        suspend fun withPagingCoordinatorResult(result: NomadMessagePagingResult): Arrangement =
            copy(usePagingCoordinator = true).also {
                everySuspend { it.pagingCoordinator.fetchOlderMessagesIfNeeded(any(), any(), any(), any()) } returns result
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
