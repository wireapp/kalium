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
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageExtensions
import io.mockative.any
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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

        val kaliumPager = KaliumPager(pager, fakePagingSource, StandardTestDispatcher())
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
                any()
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        val messageDaoExtensions: MessageExtensions = mock(MessageExtensions::class)
        private val messageDAO: MessageDAO = mock(MessageDAO::class)
        private val messageMapper: MessageMapper = mock(MessageMapper::class)

        init {

            every {
                messageMapper.fromEntityToMessage(any())
            }.returns(MESSAGE)

            every {
                messageDAO.platformExtensions
            }.returns(messageDaoExtensions)
        }

        fun withMessageExtensionsReturningPager(kaliumPager: KaliumPager<MessageEntity>) = apply {
            every {
                messageDaoExtensions.getPagerForConversation(any(), any(), any(), any())
            }.returns(kaliumPager)
        }

        private val messageRepositoryExtensions: MessageRepositoryExtensions by lazy {
            MessageRepositoryExtensionsImpl(
                messageDAO,
                messageMapper
            )
        }

        fun arrange() = this to messageRepositoryExtensions

    }

    private companion object {
        val CONVERSATION_ID_ENTITY = TestConversation.ENTITY_ID
        val MESSAGE = TestMessage.TEXT_MESSAGE
    }
}
