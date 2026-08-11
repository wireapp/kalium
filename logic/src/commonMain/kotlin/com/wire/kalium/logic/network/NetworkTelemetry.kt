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
package com.wire.kalium.logic.network

import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.network.NetworkState

internal const val NETWORK_TELEMETRY_LEADING_MESSAGE = "Network telemetry"

internal enum class NetworkTelemetryEvent {
    NETWORK_CAPABILITIES_CHANGED,
    NETWORK_LOST,
    NETWORK_UNAVAILABLE,
    NETWORK_AVAILABLE,
    NETWORK_LOSING,
    NETWORK_BLOCKED_STATUS_CHANGED,
}

internal fun KaliumLogger.logNetworkTelemetry(
    event: NetworkTelemetryEvent,
    level: KaliumLogLevel = KaliumLogLevel.INFO,
    data: Map<String, Any?> = emptyMap(),
) {
    logStructuredJson(
        level = level,
        leadingMessage = NETWORK_TELEMETRY_LEADING_MESSAGE,
        jsonStringKeyValues = buildMap {
            put("schemaVersion", 1)
            put("event", event.name)
            put("component", "NETWORK_OBSERVER")
            putAll(data)
        }
    )
}

internal fun NetworkState.telemetryName(): String = when (this) {
    NetworkState.ConnectedWithInternet -> "CONNECTED_WITH_INTERNET"
    NetworkState.ConnectedWithoutInternet -> "CONNECTED_WITHOUT_INTERNET"
    NetworkState.NotConnected -> "NOT_CONNECTED"
}
