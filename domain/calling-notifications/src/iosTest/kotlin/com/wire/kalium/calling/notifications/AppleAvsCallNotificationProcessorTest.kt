/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.calling.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppleAvsCallNotificationProcessorTest {
    @Test
    fun nativeCallStartsMarkBothOwnOtherDeviceAndPeerAsRinging() {
        val observations = mutableMapOf<String, List<Boolean>>()
        val expected = mutableMapOf<String, List<Boolean>>()
        val scenarios = listOf(
            "SETUP" to AvsCallNotificationConversationType.OneOnOne,
            "GROUPSTART" to AvsCallNotificationConversationType.Group,
            "CONFSTART" to AvsCallNotificationConversationType.ConferenceMls,
        )
        for ((type, conversationType) in scenarios) {
            for (own in listOf(false, true)) {
                val account = "start-$type-$own"
                val callbacks = Callbacks()
                val processor = processor(account, callbacks)
                try {
                    assertSuccess(processor.process(listOf(event(
                        "call",
                        payload = signal(type),
                        sender = if (own) "$account@example.com" else "peer@example.com",
                        conversationType = conversationType,
                    ))))
                } finally {
                    processor.close()
                }
                observations[account] = callbacks.ringFlags
                expected[account] = listOf(true)
            }
        }
        // Presentation policy must suppress self calls; the native callback does not.
        assertEquals(expected, observations)
    }

    @Test
    fun givenClosedRequest_whenNextRequestUsesSameAccount_thenCallbacksReachOnlyNewRequest() {
        val firstCallbacks = Callbacks()
        val first = processor("repeat", firstCallbacks)
        assertSuccess(first.process(listOf(event("first"))))
        first.close()
        first.close()

        val secondCallbacks = Callbacks()
        val second = processor("repeat", secondCallbacks)
        assertSuccess(second.process(listOf(event("second"))))
        second.close()

        assertEquals(listOf("first"), firstCallbacks.incoming)
        assertEquals(listOf("second"), secondCallbacks.incoming)
        assertIs<AvsCallNotificationProcessingResult.Failure>(first.process(listOf(event("closed"))))
        assertEquals(listOf("first"), firstCallbacks.incoming)
    }

    @Test
    fun givenTwoRequests_whenOldRequestCloses_thenNewRequestStillReceivesCallbacks() {
        val firstCallbacks = Callbacks()
        val first = processor("overlapping-requests", firstCallbacks)
        val secondCallbacks = Callbacks()
        val second = processor("overlapping-requests", secondCallbacks)
        first.close()

        assertSuccess(second.process(listOf(event("second"))))
        second.close()

        assertEquals(emptyList(), firstCallbacks.incoming)
        assertEquals(listOf("second"), secondCallbacks.incoming)
    }

    @Test
    fun givenDifferentAccountsAndClients_whenBatchesAlternate_thenCallbacksRemainIsolated() {
        val firstCallbacks = Callbacks()
        val secondCallbacks = Callbacks()
        val otherClientCallbacks = Callbacks()
        val first = processor("account-one", firstCallbacks)
        val second = processor("account-two", secondCallbacks)
        val otherClient = processor("account-one", otherClientCallbacks, client = "other-client")

        assertSuccess(first.process(listOf(event("first"))))
        assertSuccess(second.process(listOf(event("second"))))
        assertSuccess(otherClient.process(listOf(event("other-client"))))
        assertSuccess(first.process(listOf(event("first-again"))))
        first.close()
        second.close()
        otherClient.close()

        assertEquals(listOf("first", "first-again"), firstCallbacks.incoming)
        assertEquals(listOf("second"), secondCallbacks.incoming)
        assertEquals(listOf("other-client"), otherClientCallbacks.incoming)
    }

    @Test
    fun givenStartAndEndInBatch_whenProcessing_thenReportsMissedCallAtBatchEnd() {
        val callbacks = Callbacks()
        val processor = processor("missed", callbacks)

        assertSuccess(processor.process(listOf(event("missed"), event("missed", type = "CONFEND"))))
        assertSuccess(processor.process(listOf(event("closed", type = "CONFEND"))))
        processor.close()

        assertEquals(emptyList(), callbacks.incoming)
        assertEquals(listOf("missed"), callbacks.missed)
        assertEquals(listOf("closed"), callbacks.closed)
    }

    // AVS 10.5.35 stepped twice per unmatched queued event while looking for the
    // incoming call to end, so one unmatched event crashed the NSE (fixed in 10.5.41).
    @Test
    fun givenUnmatchedQueuedEvent_whenConferenceEnds_thenReportsEveryCall() {
        val callbacks = Callbacks()
        val processor = processor("unmatched-end", callbacks)

        assertSuccess(processor.process(listOf(event("ringing"), event("ended", type = "CONFEND"))))
        assertSuccess(processor.process(listOf(event("first", type = "CONFEND"), event("second", type = "CONFEND"))))
        processor.close()

        assertEquals(listOf("ringing"), callbacks.incoming)
        assertEquals(emptyList(), callbacks.missed)
        assertEquals(listOf("ended", "first", "second"), callbacks.closed)
    }

    @Test
    fun givenFailedBatch_whenNextRequestProcesses_thenCallbacksAreRebound() {
        val failedCallbacks = Callbacks()
        val failed = processor("failed", failedCallbacks)
        assertIs<AvsCallNotificationProcessingResult.Failure>(failed.process(emptyList()))
        assertIs<AvsCallNotificationProcessingResult.Failure>(
            failed.process(listOf(event("malformed", payload = "{")))
        )
        failed.close()

        val callbacks = Callbacks()
        val next = processor("failed", callbacks)
        assertSuccess(next.process(listOf(event("next"))))
        next.close()

        assertEquals(emptyList(), failedCallbacks.incoming)
        assertEquals(listOf("next"), callbacks.incoming)
    }

    @Test
    fun givenMalformedEventBeforeValidCall_whenProcessing_thenStillEvaluatesValidCall() {
        val callbacks = Callbacks()
        val processor = processor("malformed-prefix", callbacks)

        assertIs<AvsCallNotificationProcessingResult.Failure>(
            processor.process(listOf(event("malformed", payload = "{"), event("valid")))
        )
        processor.close()

        assertEquals(listOf("valid"), callbacks.incoming)
    }

    @Test
    fun givenCallbackStartsAnotherBatchForSameAccount_thenRejectsReentryAndPreservesCurrentBatch() {
        val callbacks = Callbacks()
        val nestedCallbacks = Callbacks()
        val processor = processor("reentry", callbacks)
        val nested = processor("reentry", nestedCallbacks)
        var nestedResult: AvsCallNotificationProcessingResult? = null
        callbacks.onIncoming = { nestedResult = nested.process(listOf(event("nested"))) }

        assertSuccess(processor.process(listOf(event("outer"))))
        assertIs<AvsCallNotificationProcessingResult.Failure>(nestedResult)
        assertSuccess(nested.process(listOf(event("after"))))
        processor.close()
        nested.close()

        assertEquals(listOf("outer"), callbacks.incoming)
        assertEquals(listOf("after"), nestedCallbacks.incoming)
    }

    // AVS 10.5.10 accepts these signals but only emits the self-response/reject
    // callback when senderClientId == the receiving client, not another device.
    // Use distinct account identities to force fresh native handles. Opening another
    // wrapper for the same identity would reuse process-global native state.
    @Test
    fun otherDeviceAnswerInFreshHandleOrLaterBatchDoesNotProduceClosed() {
        for (type in listOf("SETUP", "GROUPSTART", "CONFSTART")) {
            for (warm in listOf(false, true)) {
                val account = "answer-$type-$warm"
                val callbacks = Callbacks()
                if (warm) {
                    processor(account, Callbacks()).also {
                        assertSuccess(it.process(listOf(event("call", payload = signal(type, response = false)))))
                        it.close()
                    }
                }
                processor(account, callbacks).also {
                    assertSuccess(it.process(listOf(event("call", payload = signal(type, response = true),
                        sender = "$account@example.com"))))
                    it.close()
                }
                assertEquals(emptyList(), callbacks.incoming, "$type warm=$warm")
                assertEquals(emptyList(), callbacks.closed, "$type warm=$warm")
                assertEquals(emptyList(), callbacks.missed, "$type warm=$warm")
            }
        }
    }

    @Test
    fun otherDeviceRejectInFreshHandleOrLaterBatchDoesNotProduceClosed() {
        for (warm in listOf(false, true)) {
            val account = "reject-$warm"
            if (warm) processor(account, Callbacks()).also {
                assertSuccess(it.process(listOf(event("call"))))
                it.close()
            }
            val callbacks = Callbacks()
            processor(account, callbacks).also {
                assertSuccess(it.process(listOf(event("call", payload = signal("REJECT", session = ""),
                    sender = "$account@example.com"))))
                it.close()
            }
            assertEquals(emptyList(), callbacks.closed)
            assertEquals(emptyList(), callbacks.incoming)
            assertEquals(emptyList(), callbacks.missed)
        }
    }

    @Test
    fun cancelInFreshHandleOrLaterBatchDoesNotProduceClosed() {
        for (warm in listOf(false, true)) {
            val account = "cancel-$warm"
            if (warm) processor(account, Callbacks()).also {
                assertSuccess(it.process(listOf(event("call", payload = signal("SETUP")))))
                it.close()
            }
            val callbacks = Callbacks()
            processor(account, callbacks).also {
                assertSuccess(it.process(listOf(event("call", payload = signal("CANCEL")))))
                it.close()
            }
            assertEquals(emptyList(), callbacks.closed)
            assertEquals(emptyList(), callbacks.incoming)
            assertEquals(emptyList(), callbacks.missed)
        }
    }

    @Test
    fun conferenceEndInFreshHandleAndLaterBatchPreservesReason() {
        for (warm in listOf(false, true)) {
            val account = "confend-$warm"
            if (warm) processor(account, Callbacks()).also {
                assertSuccess(it.process(listOf(event("call", payload = signal("CONFSTART")))))
                it.close()
            }
            val callbacks = Callbacks()
            processor(account, callbacks).also {
                assertSuccess(it.process(listOf(event("call", payload = signal("CONFEND")))))
                it.close()
            }
            assertEquals(listOf("call"), callbacks.closed)
            assertEquals(listOf(AvsCallNotificationClosedReason.Normal), callbacks.reasons)
        }
    }

    @Test
    fun responseFromReceivingClientProducesAnsweredElsewhereButOtherClientDoesNot() {
        val account = "receiving-client-response"
        val callbacks = Callbacks()
        processor(account, callbacks, client = "def456").also {
            assertSuccess(it.process(listOf(event("call", payload = signal("SETUP", response = true),
                sender = "$account@example.com"))))
            it.close()
        }
        assertEquals(listOf("call"), callbacks.closed)
        assertEquals(listOf(AvsCallNotificationClosedReason.AnsweredElsewhere), callbacks.reasons)
    }

    private fun signal(type: String, response: Boolean = false, session: String = "call"): String =
        // Minimal valid decoder fixtures, not SDP used to establish media.
        """{"version":"3.0","type":"$type","sessid":"$session","resp":$response,"sdp":"v=0",
"props":{"videosend":"false"},"sft_url":"https://sft.invalid","secret":"YWJjZA==","timestamp":"1","seqno":"1"}"""

    private fun processor(account: String, callbacks: Callbacks, client: String = "abc123"): AvsCallNotificationProcessor =
        AvsCallNotificationProcessorFactory.create("$account@example.com", client, callbacks)

    private fun event(
        conversation: String,
        type: String = "GROUPSTART",
        payload: String = """{"version":"3.0","type":"$type","sessid":"$conversation","resp":false}""",
        sender: String = "peer@example.com",
        conversationType: AvsCallNotificationConversationType = AvsCallNotificationConversationType.ConferenceMls,
    ): AvsCallNotification = AvsCallNotification(
        payload = payload.encodeToByteArray(),
        currentTimeSeconds = 100u,
        messageTimeSeconds = 99u,
        conversationId = conversation,
        senderUserId = sender,
        senderClientId = "def456",
        conversationType = conversationType
    )

    private fun assertSuccess(result: AvsCallNotificationProcessingResult) {
        assertEquals(AvsCallNotificationProcessingResult.Success, result)
    }

    private class Callbacks : AvsCallNotificationCallbacks {
        val incoming = mutableListOf<String>()
        val ringFlags = mutableListOf<Boolean>()
        val missed = mutableListOf<String>()
        val closed = mutableListOf<String>()
        val reasons = mutableListOf<AvsCallNotificationClosedReason>()
        var onIncoming: (() -> Unit)? = null

        override fun onIncomingCall(incomingCall: AvsIncomingCallNotification) {
            incoming += incomingCall.conversationId
            ringFlags += incomingCall.shouldRing
            onIncoming?.invoke()
        }

        override fun onMissedCall(missedCall: AvsMissedCallNotification) {
            missed += missedCall.conversationId
        }

        override fun onClosedCall(closedCall: AvsClosedCallNotification) {
            closed += closedCall.conversationId
            reasons += closedCall.reason
        }
    }
}
