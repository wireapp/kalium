/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.util

import co.touchlab.stately.concurrency.AtomicReference
import kotlin.time.Duration

/**
 * Provides exponentially growing durations for retry backoff.
 *
 * Implementations must be safe for concurrent use: [reset] may be called from a different
 * coroutine (e.g. a new sync request arriving) while [next] runs in the sync managers' scope.
 */
public interface ExponentialDurationHelper {
    public fun reset()
    public fun next(): Duration
}

internal class ExponentialDurationHelperImpl(
    private val initialDuration: Duration,
    private val maxDuration: Duration,
    private val factor: Double = 2.0,
) : ExponentialDurationHelper {

    // Wrapper class to avoid issues with Duration's equals/hashCode in AtomicReference
    private data class DurationState(val value: Duration)

    private val currentDuration = AtomicReference(DurationState(initialDuration))

    override fun reset() {
        currentDuration.set(DurationState(initialDuration))
    }

    override fun next(): Duration {
        while (true) {
            val current = currentDuration.get()
            val next = DurationState(current.value.times(factor).coerceAtMost(maxDuration))

            if (currentDuration.compareAndSet(current, next)) {
                return current.value
            }
        }
    }
}

public fun ExponentialDurationHelper(initialDuration: Duration, maxDuration: Duration): ExponentialDurationHelper =
    ExponentialDurationHelperImpl(
        initialDuration = initialDuration,
        maxDuration = maxDuration
    )
