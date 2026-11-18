/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.nse

/**
 * Per-invocation observations, synchronous on the calling thread. No account or payload data is exposed.
 * SYNC_FINISHED means catch-up and queue processing returned, not that all events were processed.
 * RECEIVER_FINISHED follows cleanup on success, failure and cancellation; native delivery may still be running.
 */
public interface KaliumNSEObserver {
    public fun onStage(stage: KaliumNSEStage)
}

public enum class KaliumNSEStage {
    ACCOUNT_LOADED, SYNC_STARTED, SYNC_FINISHED,
    CLEANUP_STARTED, CLEANUP_COMPLETED, CLEANUP_FAILED, CLEANUP_NOT_NEEDED,
    RECEIVER_FINISHED, NATIVE_DELIVERY_STARTED, NATIVE_DELIVERY_FINISHED,
}

@Suppress("TooGenericExceptionCaught")
internal fun KaliumNSEObserver?.record(stage: KaliumNSEStage) {
    try { this?.onStage(stage) } catch (_: Throwable) { /* Telemetry must not change receiving or cleanup. */ }
}
