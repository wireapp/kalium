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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class BufferedMLSMessageRecoveryTrackerTest {

    @Test
    fun givenBufferedMessagesAtThreshold_whenObserving_thenShouldNotRecover() = runTest {
        val tracker = BufferedMLSMessageRecoveryTracker()

        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP))
        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP + 1.minutes))
    }

    @Test
    fun givenBufferedMessagesBeyondThreshold_whenObserving_thenShouldRecover() = runTest {
        val tracker = BufferedMLSMessageRecoveryTracker()

        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP))
        assertTrue(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP + 1.minutes + 1.seconds))
    }

    @Test
    fun givenReverseOrderedBufferedMessagesBeyondThreshold_whenObserving_thenShouldRecover() = runTest {
        val tracker = BufferedMLSMessageRecoveryTracker()

        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP + 2.minutes))
        assertTrue(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP))
    }

    @Test
    fun givenDifferentConversationKeys_whenObserving_thenShouldTrackThemIndependently() = runTest {
        val tracker = BufferedMLSMessageRecoveryTracker()

        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP))
        assertFalse(tracker.observeBufferedMessage(OTHER_CONVERSATION_ID, null, TIMESTAMP + 2.minutes))
        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, SUBCONVERSATION_ID, TIMESTAMP + 4.minutes))
    }

    @Test
    fun givenTrackedConversation_whenClearing_thenShouldStartANewWindow() = runTest {
        val tracker = BufferedMLSMessageRecoveryTracker()

        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP))
        tracker.clear(CONVERSATION_ID, null)
        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP + 2.minutes))
    }

    @Test
    fun givenRecoveryTriggered_whenMoreMessagesAreBuffered_thenShouldUseFreshWindow() = runTest {
        val tracker = BufferedMLSMessageRecoveryTracker()

        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP))
        assertTrue(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP + 1.minutes + 1.seconds))
        assertFalse(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP + 2.minutes + 1.seconds))
        assertTrue(tracker.observeBufferedMessage(CONVERSATION_ID, null, TIMESTAMP + 2.minutes + 2.seconds))
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation-id", "domain")
        val OTHER_CONVERSATION_ID = ConversationId("other-conversation-id", "domain")
        val SUBCONVERSATION_ID = SubconversationId("subconversation-id")
        val TIMESTAMP = Instant.parse("2026-07-21T10:00:00Z")
    }
}
