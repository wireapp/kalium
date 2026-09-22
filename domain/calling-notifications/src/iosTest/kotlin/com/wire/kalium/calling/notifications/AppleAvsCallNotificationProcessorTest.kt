/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.calling.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppleAvsCallNotificationProcessorTest {
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

    private fun processor(account: String, callbacks: Callbacks, client: String = "abc123"): AvsCallNotificationProcessor =
        AvsCallNotificationProcessorFactory.create("$account@example.com", client, callbacks)

    private fun event(
        conversation: String,
        type: String = "GROUPSTART",
        payload: String = """{"version":"3.0","type":"$type","sessid":"$conversation","resp":false}"""
    ): AvsCallNotification = AvsCallNotification(
        payload = payload.encodeToByteArray(),
        currentTimeSeconds = 100u,
        messageTimeSeconds = 99u,
        conversationId = conversation,
        senderUserId = "peer@example.com",
        senderClientId = "def456",
        conversationType = AvsCallNotificationConversationType.ConferenceMls
    )

    private fun assertSuccess(result: AvsCallNotificationProcessingResult) {
        assertEquals(AvsCallNotificationProcessingResult.Success, result)
    }

    private class Callbacks : AvsCallNotificationCallbacks {
        val incoming = mutableListOf<String>()
        val missed = mutableListOf<String>()
        val closed = mutableListOf<String>()
        var onIncoming: (() -> Unit)? = null

        override fun onIncomingCall(incomingCall: AvsIncomingCallNotification) {
            incoming += incomingCall.conversationId
            onIncoming?.invoke()
        }

        override fun onMissedCall(missedCall: AvsMissedCallNotification) {
            missed += missedCall.conversationId
        }

        override fun onClosedCall(closedCall: AvsClosedCallNotification) {
            closed += closedCall.conversationId
        }
    }
}
