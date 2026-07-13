/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.nse.feasibility

import com.wire.kalium.calling.notifications.AvsCallNotificationCallbacks
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessorFactory
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessingFailure
import com.wire.kalium.calling.notifications.AvsCallNotificationProcessingResult
import com.wire.kalium.calling.notifications.AvsClosedCallNotification
import com.wire.kalium.calling.notifications.AvsIncomingCallNotification
import com.wire.kalium.calling.notifications.AvsMissedCallNotification

/** This API exists only in artifacts built with `-PnseFeasibility.includeAvs=true`. */
public fun probeNotificationAvsLinkage(): FeasibilityProbeResult {
    val startedAt = kotlin.time.TimeSource.Monotonic.markNow()
    return runCatching {
        val processor = AvsCallNotificationProcessorFactory.create(
            selfUserId = "feasibility-user",
            selfClientId = "feasibility-client",
            callbacks = NoOpAvsCallbacks
        )
        try {
            when (val result = processor.process(emptyList())) {
                is AvsCallNotificationProcessingResult.Failure -> {
                    check(result.reason == AvsCallNotificationProcessingFailure.EmptyEvents) {
                        "Unexpected AVS result: $result"
                    }
                    "factory created; empty event validation returned; close balanced"
                }

                AvsCallNotificationProcessingResult.Success -> "factory created; close balanced"
            }
        } finally {
            processor.close()
        }
    }.fold(
        onSuccess = { detail ->
            FeasibilityProbeResult("notification-avs-linkage", true, startedAt.elapsedNow().inWholeNanoseconds, detail)
        },
        onFailure = { failure ->
            FeasibilityProbeResult(
                "notification-avs-linkage",
                false,
                startedAt.elapsedNow().inWholeNanoseconds,
                failure.message ?: failure.toString()
            )
        }
    )
}

private object NoOpAvsCallbacks : AvsCallNotificationCallbacks {
    override fun onIncomingCall(incomingCall: AvsIncomingCallNotification) = Unit
    override fun onMissedCall(missedCall: AvsMissedCallNotification) = Unit
    override fun onClosedCall(closedCall: AvsClosedCallNotification) = Unit
}
