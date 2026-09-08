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

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

@State(Scope.Thread)
class MarkReadBenchmark {
    private var hasUnreadEvents = true

    @Setup(Level.Invocation)
    fun insertUnreadMessage(state: MessageDatabase): Unit = runBlocking {
        state.db.messageDAO.insertOrIgnoreMessage(state.fixture.incoming)
    }

    @Benchmark
    fun markRead(state: MessageDatabase) = runBlocking {
        val message = state.fixture.incoming
        hasUnreadEvents = state.db.conversationDAO.updateReadDateAndGetHasUnreadEvents(message.conversationId, message.date)
    }

    @TearDown(Level.Invocation)
    fun reset(state: MessageDatabase) = runBlocking {
        check(!hasUnreadEvents)
        val message = state.fixture.incoming
        state.db.messageDAO.deleteMessage(message.id, message.conversationId)
        state.fixture.restoreDates(state.db)
    }
}
