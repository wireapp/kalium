/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

/**
 * Sentinel marker, that serves as an indicator that the current initial sync has finished for this websocket session.
 */
internal sealed class SentinelMarker {
    /**
     * No current marker, used for the legacy system or when the initial sync is finished.
     */
    internal data object None : SentinelMarker()

    /**
     * A marker for the current initial sync, with a uuid [value] that will be compared against the sentinel event sent by the server.
     */
    internal data class Marker(val value: String) : SentinelMarker()

    /**
     * Returns the marker, if any, otherwise an empty string.
     */
    internal fun getMarker(): String {
        return when (this) {
            is Marker -> value
            else -> ""
        }
    }
}
