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
package com.wire.kalium.benchmarks.persistence

import androidx.paging.PagingConfig
import com.wire.kalium.benchmarks.fixtures.firstPage
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

@State(Scope.Thread)
class MessagePagingBenchmark {
    @Param("0", "5000")
    var offset: Int = 0
    private var page: List<MessageEntity> = emptyList()

    @Benchmark
    fun loadInitialOrRestoredPage(state: MessageDatabase) = runBlocking {
        page = firstPage(state.db.messageDAO.platformExtensions.getPagerForConversation(
            conversationId = state.fixture.conversation.id,
            visibilities = listOf(MessageEntity.Visibility.VISIBLE),
            pagingConfig = PagingConfig(pageSize = 50, initialLoadSize = 50, enablePlaceholders = false),
            startingOffset = offset.toLong(),
        ).pagingDataFlow)
    }

    @TearDown(Level.Invocation)
    fun checkPageAndReleaseSource(state: MessageDatabase) = runBlocking {
        check(page.map { it.id } == state.fixture.expectedPageIds(offset, 50))
        // Invalidate the cancelled collection's source to detach its SQLDelight listener.
        val message = state.fixture.incoming
        state.db.messageDAO.insertOrIgnoreMessage(message)
        state.db.messageDAO.deleteMessage(message.id, message.conversationId)
        state.fixture.restoreDates(state.db)
    }
}
