/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallSignalOutcomeTest {
    @Test
    fun onlyExpiredOneToOneStartsCanBeMissedWithoutATermination() {
        val start = signal("start")
        assertTrue(resolve(start, now = 60_000).isEmpty())
        assertEquals(listOf(CallSignalOutcome(start, true)), resolve(start, now = 60_001))
        assertTrue(resolve(start.copy(isGroup = true), now = 90_000).isEmpty())
    }

    @Test
    fun ownAnswerAndRejectPreventMissedEvenWhenAnswerUsesAnotherSession() {
        val start = signal("start")
        for (type in listOf("SETUP", "REJECT")) {
            val answer = signal("answer", type, time = 10_000, self = true, response = type == "SETUP", session = "other")
            val end = signal("end", "CANCEL", time = 20_000)
            assertEquals(listOf(CallSignalOutcome(start, false)), resolve(start, answer, end))
        }
    }

    @Test
    fun conferenceRequiresMatchingEndAndOwnJoinWinsOverEnd() {
        val start = signal("start", "CONFSTART").copy(isGroup = true)
        val unrelatedEnd = signal("other-end", "CONFEND", time = 10_000, session = "other")
        assertTrue(resolve(start, unrelatedEnd).isEmpty())
        val end = signal("end", "CONFEND", time = 20_000)
        assertEquals(listOf(CallSignalOutcome(start, true)), resolve(start, end))
        val oneToOneConference = start.copy(isGroup = false)
        assertEquals(listOf(CallSignalOutcome(oneToOneConference, true)), resolve(oneToOneConference, end))
        val join = signal("join", "CONFSTART", time = 10_000, self = true)
        assertEquals(listOf(CallSignalOutcome(start, false)), resolve(start, join, end))
        assertEquals(listOf(CallSignalOutcome(start, false, complete = false)), resolve(start, join))
        assertEquals(listOf(CallSignalOutcome(oneToOneConference, false, complete = false)), resolve(oneToOneConference, join))
    }

    @Test
    fun laterCallsOtherConversationsAndPeerResponsesCannotAnswerEarlierCall() {
        val start = signal("start")
        val next = signal("next", time = 20_000, session = "next")
        val answer = signal("answer", time = 25_000, self = true, response = true, session = "next")
        assertEquals(listOf(CallSignalOutcome(start, true), CallSignalOutcome(next, false)), resolve(start, next, answer))
        assertEquals(listOf(CallSignalOutcome(start, true)), resolve(start, answer.copy(conversationDomain = "other.example")))
        assertEquals(listOf(CallSignalOutcome(start, true)), resolve(start, answer.copy(senderValue = "peer")))
        assertEquals(listOf(CallSignalOutcome(start, true)), resolve(start, signal("outgoing", time = 25_000, self = true, session = "other")))
    }

    @Test
    fun ownStartsUnreadyMalformedAndNonPendingStartsAreNotInferred() {
        assertTrue(resolve(signal("own", self = true)).isEmpty())
        assertTrue(resolve(signal("unready").copy(ready = false)).isEmpty())
        for (payload in listOf("{", "{}", """{"version":"3.0","type":"SETUP","sessid":"call","resp":false}""")) {
            assertTrue(resolve(signal("malformed").copy(payload = payload)).isEmpty())
        }
        assertTrue(reconcileCallSignals(listOf(signal("not-pending")), emptySet(), SELF, 90_000).isEmpty())
    }

    @Test
    fun duplicateStartAndUnsortedSignalsStillResolveTheOriginalIdentity() {
        val start = signal("start")
        val duplicate = signal("duplicate", time = 1_000)
        assertEquals(listOf(CallSignalOutcome(start, true)), resolve(duplicate, start))
    }

    @Test
    fun freshCancelledOneToOneIsMissedAndWrongClientCancellationIsIgnored() {
        val start = signal("start")
        val end = signal("end", "CANCEL", time = 10_000)
        assertEquals(listOf(CallSignalOutcome(start, true)), resolve(start, end, now = 20_000))
        assertTrue(resolve(start, end.copy(senderClientId = "other"), now = 20_000).isEmpty())
    }

    private fun resolve(vararg signals: CallSignalRecord, now: Long = 90_000) =
        reconcileCallSignals(signals.toList(), signals.map { it.messageId }.toSet(), SELF, now)

    private fun signal(
        id: String,
        type: String = "SETUP",
        time: Long = 0,
        self: Boolean = false,
        response: Boolean = false,
        session: String = "call",
    ) = CallSignalRecord(
        id, id, "conversation", "example.com", if (self) "self" else "peer", "example.com", "client",
        """{"version":"3.0","type":"$type","sessid":"$session","resp":$response,"sdp":"v=0"}""",
        time, false, false, 0, ready = true,
    )

    private companion object {
        val SELF = QualifiedIDEntity("self", "example.com")
    }
}
