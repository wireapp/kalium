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

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventProcessingPerformanceData
import com.wire.kalium.common.logger.logStructuredJson
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The `EventProcessingLogger` class is responsible for logging event processing details.
 *
 * @property logger The underlying logging implementation used for logging.
 * @property event The event being processed.
 * @property startOfProcessing The start time of event processing. Defaults to the current system time.
 */
internal class EventProcessingLogger(
    private val logger: KaliumLogger,
    private val event: Event,
    private val startOfProcessing: Instant
) {

    /**
     * Logs event processing.
     * Will use the difference between the current system time and [startOfProcessing] to log performance data.
     * Underlying implementation detail is using the common [KaliumLogger.logStructuredJson] to log structured JSON.
     */
    fun logComplete(
        status: EventLoggingStatus,
        extraInfo: Array<out Pair<String, Any>> = arrayOf()
    ) = with(logger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)) {
        val duration = Clock.System.now() - startOfProcessing
        val performanceData = EventProcessingPerformanceData.TimeTaken(duration)

        val logMap = event.toLogMap().toMutableMap()
        logMap += extraInfo

        logMap["eventPerformanceData"] = performanceData.logData

        when (status) {
            EventLoggingStatus.SUCCESS -> {
                logMap["outcome"] = "success"
                logStructuredJson(KaliumLogLevel.INFO, "Success handling event", logMap)
            }

            EventLoggingStatus.FAILURE -> {
                logMap["outcome"] = "failure"
                logStructuredJson(KaliumLogLevel.ERROR, "Failure handling event", logMap)
            }

            EventLoggingStatus.SKIPPED -> {
                logMap["outcome"] = "skipped"
                logStructuredJson(KaliumLogLevel.WARN, "Skipped handling event", logMap)
            }
        }
    }

    /**
     * Logs the event processing as successful.
     * Will use the difference between the current system time and [startOfProcessing] to log performance data.
     */
    fun logSuccess(vararg extraInfo: Pair<String, Any>) {
        logComplete(EventLoggingStatus.SUCCESS, extraInfo)
    }

    /**
     * Logs the event processing as failed.
     * Will use the difference between the current system time and [startOfProcessing] to log performance data.
     */
    fun logFailure(failure: CoreFailure? = null, vararg extraInfo: Pair<String, Any>) {
        if (failure != null) {
            logComplete(EventLoggingStatus.FAILURE, arrayOf("error" to failure, *extraInfo))
        } else {
            logComplete(EventLoggingStatus.FAILURE, extraInfo)
        }
    }
}

internal enum class EventLoggingStatus {
    SUCCESS,
    FAILURE,
    SKIPPED
}

internal fun KaliumLogger.createEventProcessingLogger(
    event: Event
) = EventProcessingLogger(this, event, Clock.System.now())
