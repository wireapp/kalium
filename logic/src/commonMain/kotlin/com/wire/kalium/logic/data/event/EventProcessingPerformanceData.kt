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
package com.wire.kalium.logic.data.event

import kotlin.time.Duration

/**
 * Hierarchy to represent possible ways of recording the
 * performance of event processing.
 */
sealed interface EventProcessingPerformanceData {

    /**
     * Map containing information about the performance,
     * which can be used for logging and analysis
     */
    val logData: Map<String, Any>?

    /**
     * No performance is recorded for these
     */
    data object None : EventProcessingPerformanceData {
        override val logData: Map<String, Any>?
            get() = null
    }

    /**
     * The measurement of time that it took to process an event
     */
    data class TimeTaken(
        val duration: Duration,
    ) : EventProcessingPerformanceData {
        private val durationEntry: Pair<String, Long>
            get() = "timeTakenInMillis" to duration.inWholeMilliseconds

        override val logData: Map<String, Any>
            get() = mapOf(durationEntry)
    }
}
