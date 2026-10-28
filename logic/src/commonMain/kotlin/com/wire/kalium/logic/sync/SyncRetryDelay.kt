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
package com.wire.kalium.logic.sync

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.SyncRetryStatus
import com.wire.kalium.logic.network.telemetryName
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.network.NetworkStateObserver
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Waits for [retryDelay] before the next sync retry attempt, completing earlier
 * if internet connectivity comes back in the meantime.
 *
 * On reconnection, [exponentialDurationHelper] is reset: if the retry fails right away
 * (e.g. DNS is still catching up), the next failure backs off from the minimum delay
 * instead of inheriting the stale pre-reconnect backoff.
 */
@Suppress("LongParameterList")
internal suspend fun NetworkStateObserver.delayBeforeSyncRetry(
    retryDelay: Duration,
    exponentialDurationHelper: ExponentialDurationHelper,
    logger: KaliumLogger,
    syncType: SyncType,
    timeSource: TimeSource = TimeSource.Monotonic,
    onRetryStateChanged: suspend (SyncRetryStatus) -> Unit = {},
    retryController: SyncRetryController = SyncRetryController(),
    failureGeneration: Long? = null,
) {
    val schedule = SyncRetryStatus.Scheduled(Uuid.random().toString(), timeSource.markNow() + retryDelay)
    val component = syncType.telemetryComponent()
    val trigger = try {
        retryController.awaitRetry(
            failureGeneration = failureGeneration ?: retryController.generation(),
            onRegistered = {
                onRetryStateChanged(schedule)
                logger.logSyncTelemetry(
                    event = SyncTelemetryEvent.RETRY_WAIT_STARTED,
                    component = component,
                    data = retryTelemetryData(retryDelay)
                )
            },
            waitForNetworkOrTimeout = { delayUntilConnectedWithInternetAgain(schedule.remainingDelay()) },
        ).also { onRetryStateChanged(SyncRetryStatus.Attempting(schedule.id)) }
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable) { onRetryStateChanged(SyncRetryStatus.None) }
        throw cancelled
    }
    if (trigger != RetryTrigger.RETRY_DELAY_ELAPSED) {
        exponentialDurationHelper.reset()
    }
    logger.logSyncTelemetry(
        event = SyncTelemetryEvent.RETRY_TRIGGERED,
        component = component,
        data = retryTelemetryData(retryDelay) + mapOf(
            "trigger" to trigger.name,
            "backoffReset" to (trigger != RetryTrigger.RETRY_DELAY_ELAPSED),
        )
    )
}

private fun NetworkStateObserver.retryTelemetryData(retryDelay: Duration): Map<String, Any?> = buildMap {
    put("retryDelayInMillis", retryDelay.inWholeMilliseconds)
    put("networkState", observeNetworkState().value.telemetryName())
    observeCurrentNetwork().value?.id?.let { put("networkId", it) }
}
