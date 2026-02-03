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

package com.wire.kalium.logic.feature.user.webSocketStatus

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import kotlinx.datetime.Instant

/**
 * Use case to get the timestamp of the last WebSocket event received.
 * This can be used to detect stale WebSocket connections that stopped receiving events
 * without proper disconnection notification.
 */
public interface GetLastWebSocketEventInstantUseCase {
    /**
     * Returns the timestamp when the last WebSocket event was received.
     *
     * @return The [Instant] when the last WebSocket event was received, or null if no events were received yet.
     */
    public operator fun invoke(): Instant?
}

internal class GetLastWebSocketEventInstantUseCaseImpl(
    private val incrementalSyncRepository: IncrementalSyncRepository
) : GetLastWebSocketEventInstantUseCase {
    override fun invoke(): Instant? = incrementalSyncRepository.lastWebSocketEventInstant()
}
