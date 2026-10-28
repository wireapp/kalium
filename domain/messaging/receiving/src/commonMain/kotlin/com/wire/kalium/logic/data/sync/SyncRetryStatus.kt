/* Wire, Copyright (C) 2026 Wire Swiss GmbH. SPDX-License-Identifier: GPL-3.0-only */
package com.wire.kalium.logic.data.sync

import kotlin.time.Duration
import kotlin.time.TimeMark

/** Replayable state of the actual retry execution, never an estimate made by a UI collector. */
sealed interface SyncRetryStatus {
    data object None : SyncRetryStatus

    /** Process-local monotonic deadline. A new wait always has a new [id]. */
    data class Scheduled(val id: String, private val deadline: TimeMark) : SyncRetryStatus {
        fun remainingDelay(): Duration = (-deadline.elapsedNow()).coerceAtLeast(Duration.ZERO)
    }

    data class Attempting(val id: String) : SyncRetryStatus
}
