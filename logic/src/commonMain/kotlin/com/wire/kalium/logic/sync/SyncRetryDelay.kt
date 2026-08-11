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
import com.wire.kalium.logic.network.telemetryName
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.network.NetworkStateObserver
import kotlin.time.Duration

/**
 * Waits for [retryDelay] before the next sync retry attempt, completing earlier
 * if internet connectivity comes back in the meantime.
 *
 * On reconnection, [exponentialDurationHelper] is reset: if the retry fails right away
 * (e.g. DNS is still catching up), the next failure backs off from the minimum delay
 * instead of inheriting the stale pre-reconnect backoff.
 */
internal suspend fun NetworkStateObserver.delayBeforeSyncRetry(
    retryDelay: Duration,
    exponentialDurationHelper: ExponentialDurationHelper,
    logger: KaliumLogger,
    syncType: SyncType,
) {
    val component = syncType.telemetryComponent()
    logger.logSyncTelemetry(
        event = SyncTelemetryEvent.RETRY_WAIT_STARTED,
        component = component,
        data = retryTelemetryData(retryDelay)
    )
    val reconnected = delayUntilConnectedWithInternetAgain(retryDelay)
    if (reconnected) {
        exponentialDurationHelper.reset()
    }
    logger.logSyncTelemetry(
        event = SyncTelemetryEvent.RETRY_TRIGGERED,
        component = component,
        data = retryTelemetryData(retryDelay) + mapOf(
            "trigger" to if (reconnected) "NETWORK_RECONNECTED" else "RETRY_DELAY_ELAPSED",
            "backoffReset" to reconnected,
        )
    )
}

private fun NetworkStateObserver.retryTelemetryData(retryDelay: Duration): Map<String, Any?> = buildMap {
    put("retryDelayInMillis", retryDelay.inWholeMilliseconds)
    put("networkState", observeNetworkState().value.telemetryName())
    observeCurrentNetwork().value?.id?.let { put("networkId", it) }
}
