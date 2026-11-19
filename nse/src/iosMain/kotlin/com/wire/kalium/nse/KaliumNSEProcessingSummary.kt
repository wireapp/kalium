/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

import com.wire.kalium.logic.sync.receiving.CatchUpOutcome
import com.wire.kalium.logic.sync.receiving.ReceivedNotificationResult
import com.wire.kalium.logic.sync.receiving.ReceivingDiagnostics

/** Event records, not messages. Interrupted attempts can undercount a final commit whose acknowledgement did not return. */
public data class KaliumNSEProcessingSummary(
    public val processedEventCount: Int,
    public val outcome: KaliumNSEProcessingOutcome,
    /** Code-defined queue stop or failure reason. No event identifiers or payload data. */
    public val stopReason: String?,
)

/**
 * Queue evidence only: COMPLETE requires live catch-up, queue end and no deferred prefix gap.
 * Later native delivery or cleanup failures do not change an already-observed queue outcome.
 */
public enum class KaliumNSEProcessingOutcome { NOT_STARTED, COMPLETE, INCOMPLETE, FAILED, CANCELLED }

/** Queue evidence survives later notification-delivery or account-cleanup failures. */
internal class NSEProcessingObservation {
    val diagnostics = ReceivingDiagnostics()
    private var outcome = KaliumNSEProcessingOutcome.NOT_STARTED
    private var stopReason: String? = null
    private var queueReturned = false

    fun started() { outcome = KaliumNSEProcessingOutcome.INCOMPLETE }

    fun processed(caughtUp: CatchUpOutcome, received: ReceivedNotificationResult) {
        queueReturned = true
        outcome = if (caughtUp == CatchUpOutcome.LIVE && received.stoppedReason == "QUEUE_END" && received.prefixComplete) {
            KaliumNSEProcessingOutcome.COMPLETE
        } else KaliumNSEProcessingOutcome.INCOMPLETE
        stopReason = received.stoppedReason ?: received.appWorkPending?.name
    }

    fun returned(result: KaliumNSEResult) {
        if (queueReturned) return
        stopReason = result.stoppedReason ?: result.failureReason
        if (outcome != KaliumNSEProcessingOutcome.NOT_STARTED && result.status == KaliumNSEStatus.FAILED) {
            outcome = if (result.failureReason == "TIMEOUT") KaliumNSEProcessingOutcome.INCOMPLETE
            else KaliumNSEProcessingOutcome.FAILED
        }
    }

    fun failed(cancelled: Boolean) {
        if (queueReturned) return
        outcome = if (cancelled) KaliumNSEProcessingOutcome.CANCELLED else KaliumNSEProcessingOutcome.FAILED
        stopReason = if (cancelled) "CANCELLED" else "EXCEPTION"
    }

    fun summary() = KaliumNSEProcessingSummary(diagnostics.processedEventCount, outcome, stopReason)
}
