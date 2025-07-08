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

import kotlinx.coroutines.flow.Flow

interface EventStream {

    /**
     * Streams event-related data as a Flow while handling potential failures.
     * This function provides an asynchronous data stream of events that can either represent
     * catching up on missed events or live events in real-time.
     *
     * @return A [Flow] of [com.wire.kalium.logic.data.event.stream.EventStreamData] upon successful function completion.
     */
    suspend fun eventFlow(): Flow<EventStreamData>

}
