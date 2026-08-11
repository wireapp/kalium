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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.sync.SyncState

internal const val SYNC_TELEMETRY_LEADING_MESSAGE = "Sync telemetry"

internal enum class SyncTelemetryEvent {
    REQUEST_COUNT_CHANGED,
    EXECUTION_STARTED,
    EXECUTION_STOPPED,
    REQUEST_RETAINED,
    SYNC_FAILED,
    RETRY_BACKOFF_RESET,
    RETRY_WAIT_STARTED,
    RETRY_TRIGGERED,
    SYNC_PROCESS_STARTED,
    SYNC_PROCESS_COMPLETED,
}

internal enum class SyncTelemetryComponent {
    EXECUTOR,
    INCREMENTAL,
    SLOW,
}

internal fun KaliumLogger.logSyncTelemetry(
    event: SyncTelemetryEvent,
    component: SyncTelemetryComponent,
    level: KaliumLogLevel = KaliumLogLevel.INFO,
    data: Map<String, Any?> = emptyMap(),
) {
    logStructuredJson(
        level = level,
        leadingMessage = SYNC_TELEMETRY_LEADING_MESSAGE,
        jsonStringKeyValues = buildMap {
            put("schemaVersion", 1)
            put("event", event.name)
            put("component", component.name)
            putAll(data)
        }
    )
}

internal fun SyncState.telemetryName(): String = when (this) {
    SyncState.Waiting -> "WAITING"
    SyncState.SlowSync -> "SLOW_SYNC"
    SyncState.GatheringPendingEvents -> "GATHERING_PENDING_EVENTS"
    SyncState.Live -> "LIVE"
    is SyncState.Failed -> "FAILED"
}

internal fun CoreFailure.telemetryData(): Map<String, Any?> = buildMap {
    put("failureType", this@telemetryData::class.simpleName)
    val cause = when (val failure = this@telemetryData) {
        is NetworkFailure.NoNetworkConnection -> failure.cause
        is NetworkFailure.ProxyError -> failure.cause
        is NetworkFailure.ServerMiscommunication -> failure.rootCause
        is CoreFailure.Unknown -> failure.rootCause
        else -> null
    }
    put("causeType", cause?.let { it::class.simpleName })
}

internal fun SyncType.telemetryComponent(): SyncTelemetryComponent = when (this) {
    SyncType.INCREMENTAL -> SyncTelemetryComponent.INCREMENTAL
    SyncType.SLOW -> SyncTelemetryComponent.SLOW
}
