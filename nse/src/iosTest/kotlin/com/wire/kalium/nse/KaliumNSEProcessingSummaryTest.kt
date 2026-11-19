/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.logic.sync.receiving.CatchUpOutcome
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KaliumNSEProcessingSummaryTest {
    @Test
    fun completenessRequiresLiveCatchUpQueueEndAndNoDeferredGap() {
        val cases = listOf(
            Triple(CatchUpOutcome.LIVE, "QUEUE_END", true) to KaliumNSEProcessingOutcome.COMPLETE,
            Triple(CatchUpOutcome.LIVE, "QUEUE_END", false) to KaliumNSEProcessingOutcome.INCOMPLETE,
            Triple(CatchUpOutcome.LIVE, "BUDGET", true) to KaliumNSEProcessingOutcome.INCOMPLETE,
            Triple(CatchUpOutcome.FAILED, "QUEUE_END", true) to KaliumNSEProcessingOutcome.INCOMPLETE,
        )
        for ((input, expected) in cases) {
            val observation = NSEProcessingObservation()
            observation.started()
            observation.processed(input.first, ReceivedNotificationResult(null,
                prefixComplete = input.third, stoppedReason = input.second))
            assertEquals(expected, observation.summary().outcome)
            assertEquals(input.second, observation.summary().stopReason)
            // Later native delivery failure or cleanup cancellation must not erase observed queue completion.
            observation.failed(cancelled = false)
            observation.failed(cancelled = true)
            assertEquals(expected, observation.summary().outcome)
        }
    }

    @Test
    fun interruptedAndUnstartedProcessingStayDistinct() {
        val observation = NSEProcessingObservation()
        assertEquals(KaliumNSEProcessingOutcome.NOT_STARTED, observation.summary().outcome)
        observation.started()
        observation.failed(cancelled = false)
        assertEquals(KaliumNSEProcessingOutcome.FAILED, observation.summary().outcome)
        val cancellation = NSEProcessingObservation()
        cancellation.started()
        cancellation.failed(cancelled = true)
        assertEquals(KaliumNSEProcessingOutcome.CANCELLED, cancellation.summary().outcome)
    }

    @Test
    fun refusedRequestReportsOnceBeforeReceiverExitEvenWhenObserverThrows() = runTest {
        val receiver = KaliumNSEReceiver(KaliumNSEConfiguration(
            rootPath = "unused", keychainService = "unused", keychainAccessGroup = null,
            keychainAccessibility = KaliumNSEKeychainAccessibility.AFTER_FIRST_UNLOCK,
            userAgent = "test", certificatePins = emptyMap(),
        ))
        val observations = mutableListOf<String>()
        val summaries = mutableListOf<KaliumNSEProcessingSummary>()
        val result = receiver.processAndDeliver(
            KaliumNSERequest("user", "example.com", "", true, 0),
            object : KaliumNSEMessageDelivery {
                override suspend fun deliver(alert: KaliumNSEAlert): Boolean = error("Unexpected delivery")
            },
            object : KaliumNSEObserver {
                override fun onStage(stage: KaliumNSEStage) { observations.add(stage.name) }
                override fun onProcessingSummary(summary: KaliumNSEProcessingSummary) {
                    observations.add("SUMMARY")
                    summaries.add(summary)
                    error("observer failed")
                }
            },
        )
        assertEquals(0, summaries.single().processedEventCount)
        assertEquals(KaliumNSEProcessingOutcome.NOT_STARTED, summaries.single().outcome)
        assertEquals("INVALID_REQUEST", summaries.single().stopReason)
        assertEquals(KaliumNSEStatus.FAILED, result.status)
        assertEquals(listOf("CLEANUP_NOT_NEEDED", "SUMMARY", "RECEIVER_FINISHED"), observations)
    }
}
