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
package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.event.EventEnvelope

sealed interface EventStreamData {

    data class NewEvents(val eventList: List<EventEnvelope>) : EventStreamData

    /**
     * Represents a state indicating that the event stream is up to date,
     * with no new events to process or retrieve.
     *
     * This signals that the current state of the event stream matches the
     * remote source of truth, implying synchronisation.
     * A.K.A. We're ONLINE, BABY!
     */
    data object IsUpToDate : EventStreamData
}
