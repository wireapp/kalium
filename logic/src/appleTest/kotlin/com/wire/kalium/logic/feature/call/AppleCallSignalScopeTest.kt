/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.sync.receiving.CallSignalJournal
import com.wire.kalium.logic.sync.receiving.CallSignalRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppleCallSignalScopeTest {
    @Test
    fun capturedCloseDoesNotConsumeLaterSignalsOrAnotherConversation() {
        val scope = AppleCallSignalScope { 1000L }
        val first = ConversationId("first", "example.com")
        val other = ConversationId("other", "example.com")
        scope.include(signal(first, "old", 1000L))
        val close = scope.snapshot(first)
        scope.include(signal(first, "next", 1000L))
        scope.include(signal(other, "other", 1000L))
        assertEquals(setOf("old"), close)
        repeat(2) { scope.forget(first, close) }
        assertEquals(setOf("next"), scope.snapshot(first))
        assertEquals(setOf("other"), scope.snapshot(other))
        scope.clear()
        assertTrue(scope.snapshot(first).isEmpty())
    }

    @Test
    fun ongoingCallDropsExpiredTrackingWithoutChangingCapturedScope() {
        var now = 1000L
        val scope = AppleCallSignalScope { now }
        val conversation = ConversationId("call", "example.com")
        scope.include(signal(conversation, "old", now))
        val close = scope.snapshot(conversation)
        now += CallSignalJournal.SIGNAL_RETENTION_MS
        scope.include(signal(conversation, "recent", now))
        assertEquals(setOf("recent"), scope.snapshot(conversation))
        assertEquals(setOf("old"), close)
    }

    private fun signal(conversation: ConversationId, id: String, storedAt: Long) = CallSignalRecord(
        id, id, conversation.value, conversation.domain, "peer", "example.com", "client", "{}",
        1000L, false, false, storedAt, ready = true
    )
}
