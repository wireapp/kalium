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
package com.wire.kalium.logic.data.call

import app.cash.turbine.test
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InCallReactionsRepositoryTest {

    @Test
    fun whenNewReactionIsAdded_thenRepositoryEmitsNewReactionMessage() = runTest {
        val repository: InCallReactionsRepository = InCallReactionsDataSource()
        val emojis = setOf("1", "2")

        repository.observeInCallReactions(conversationId).test {
            repository.addInCallReaction(conversationId, senderUserId, emojis)

            assertEquals(InCallReactionMessage(conversationId, senderUserId, emojis), awaitItem())
        }
    }

    @Test
    fun givenConversationObserver_whenOtherConversationEmits_thenOnlyMatchingReactionIsObserved() = runTest {
        val repository: InCallReactionsRepository = InCallReactionsDataSource()
        val expected = InCallReactionMessage(conversationId, senderUserId, setOf("matching"))

        repository.observeInCallReactions(conversationId).test {
            repository.addInCallReaction(otherConversationId, senderUserId, setOf("filtered"))
            repository.addInCallReaction(expected.conversationId, expected.senderUserId, expected.emojis)

            assertEquals(expected, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun givenReactionWasEmittedBeforeObservation_thenItIsNotReplayed() = runTest {
        val repository: InCallReactionsRepository = InCallReactionsDataSource()

        repository.addInCallReaction(conversationId, senderUserId, setOf("not-replayed"))

        repository.observeInCallReactions(conversationId).test {
            expectNoEvents()
        }
    }

    @Test
    fun givenSlowObserver_whenMoreThan32ReactionsAreEmitted_thenOldestBufferedReactionIsDropped() = runTest {
        val repository: InCallReactionsRepository = InCallReactionsDataSource()
        val firstReactionObserved = CompletableDeferred<Unit>()
        val releaseFirstReaction = CompletableDeferred<Unit>()
        val observedIndexes = mutableListOf<Int>()
        val observer = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeInCallReactions(conversationId).collect { reaction ->
                val index = reaction.emojis.single().toInt()
                observedIndexes += index
                if (index == 0) {
                    firstReactionObserved.complete(Unit)
                    releaseFirstReaction.await()
                }
            }
        }

        repository.addInCallReaction(conversationId, senderUserId, setOf("0"))
        runCurrent()
        firstReactionObserved.await()
        (1..33).forEach { index ->
            repository.addInCallReaction(conversationId, senderUserId, setOf(index.toString()))
        }

        releaseFirstReaction.complete(Unit)
        runCurrent()

        assertEquals(listOf(0) + (2..33), observedIndexes)
        observer.cancel()
    }

    private companion object {
        val conversationId = ConversationId("conversation-id", "wire.example")
        val otherConversationId = ConversationId("other-conversation-id", "wire.example")
        val senderUserId = UserId("sender-id", "wire.example")
    }
}
