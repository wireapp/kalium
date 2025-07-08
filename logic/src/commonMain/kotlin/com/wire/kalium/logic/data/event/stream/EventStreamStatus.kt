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
package com.wire.kalium.logic.data.event.stream

/**
 * Represents the current status of the event stream connection and processing state.
 * The event stream is responsible for receiving real-time updates and notifications.
 */
sealed interface EventStreamStatus {

    /**
     * The event stream is in its initial setup phase, establishing connection
     * and preparing for event processing. Consuming events is not yet advisable,
     * as we need to invalidate and sanitise previously stored events.
     */
    data object Initializing : EventStreamStatus

    /**
     * The event stream is processing pending or historical events
     * missed while the client was offline or disconnected.
     */
    data object CatchingUp : EventStreamStatus

    /**
     * The event stream is actively receiving and forwarding real-time events
     * as they are received from remote.
     *
     * @property lastPendingEventId The ID of the last known pending event.
     * After this event is processed, the app can be considered "Live".
     * If null, there are no known pending events.
     */
    data class Live(val lastPendingEventId: String?) : EventStreamStatus

    /**
     * The server terminated the event stream connection. This is
     * unexpected. We should retry.
     */
    data object ClosedRemotely : EventStreamStatus
}
