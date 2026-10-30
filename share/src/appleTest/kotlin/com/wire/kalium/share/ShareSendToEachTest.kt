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
package com.wire.kalium.share

import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareSendToEachTest {

    @Test
    fun eachConversationIsSentInTurnAndReportsItsOutcome() = runTest {
        val outcomes = mutableMapOf<ConversationId, ShareOutcome>()

        val sent = sendToEach(items(FIRST, SECOND), outcomes, shouldStop = { false }, stored = { true }) { conversationId, _, _ ->
            if (conversationId == FIRST) ShareOutcome(ShareStatus.OPEN_APP, "LEGAL_HOLD") else ShareOutcome(ShareStatus.SENT)
        }

        assertEquals(listOf(FIRST, SECOND), sent.keys.toList())
        assertEquals(ShareOutcome(ShareStatus.SENT), sent.getValue(SECOND))
    }

    @Test
    fun conversationsNotReachedWhenSendingIsCutShortKeepTheirMessagesAsFailed() = runTest {
        // The second conversation stored a message before the budget ended, so it keeps its own rest.
        val (calls, outcomes) = cutShortWhileSendingTheSecond(secondStored = true)

        assertEquals(listOf(FIRST to false, SECOND to false, THIRD to true), calls)
        assertEquals(setOf(FIRST, THIRD), outcomes.keys)
        assertEquals(ShareStatus.FAILED, outcomes.getValue(THIRD).status)
    }

    @Test
    fun aConversationCutShortBeforeItStoredAnythingKeepsItsMessagesAsFailedToo() = runTest {
        val (calls, outcomes) = cutShortWhileSendingTheSecond(secondStored = false)

        assertEquals(listOf(FIRST to false, SECOND to false, SECOND to true, THIRD to true), calls)
        assertEquals(setOf(FIRST, SECOND, THIRD), outcomes.keys)
    }

    @Test
    fun aShareCutShortAfterStoringReportsEachConversation() {
        val blocked = mapOf(SECOND to ShareOutcome(ShareStatus.OPEN_APP, "LEGAL_HOLD"))

        // Nothing reached a conversation or was stored: the share failed as a whole.
        assertEquals(null, timedOutOutcomes(listOf(FIRST, SECOND), blocked, emptyMap(), emptyMap()))
        // The first conversation stored a message, then the budget ended while it sent.
        assertEquals(
            listOf(FIRST to ShareOutcome(ShareStatus.FAILED, TIMEOUT, "stored"), SECOND to blocked.getValue(SECOND)),
            timedOutOutcomes(listOf(FIRST, SECOND), blocked, emptyMap(), mapOf(FIRST to "stored")),
        )
    }

    /** The budget ends while the second conversation sends. */
    private suspend fun kotlinx.coroutines.test.TestScope.cutShortWhileSendingTheSecond(
        secondStored: Boolean,
    ): Pair<List<Pair<ConversationId, Boolean>>, Map<ConversationId, ShareOutcome>> {
        val outcomes = mutableMapOf<ConversationId, ShareOutcome>()
        val calls = mutableListOf<Pair<ConversationId, Boolean>>()
        val sending = launch {
            sendToEach(
                items(FIRST, SECOND, THIRD),
                outcomes,
                shouldStop = { false },
                stored = { it == SECOND && secondStored },
            ) { conversationId, _, shouldStop ->
                calls += conversationId to shouldStop()
                when {
                    shouldStop() -> ShareOutcome(ShareStatus.FAILED, "OUT_OF_TIME", "kept-$conversationId")
                    conversationId == SECOND -> awaitCancellation()
                    else -> ShareOutcome(ShareStatus.SENT, messageId = "sent")
                }
            }
        }
        runCurrent()
        sending.cancelAndJoin()
        return calls to outcomes
    }

    private fun items(vararg conversationIds: ConversationId) =
        conversationIds.associateWith { listOf<ShareItem>(ShareItem.Text("Hello")) }

    private companion object {
        val FIRST = ConversationId("first", "wire.com")
        val SECOND = ConversationId("second", "wire.com")
        val THIRD = ConversationId("third", "wire.com")
    }
}
