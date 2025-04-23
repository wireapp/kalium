/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.util

import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mockable
import kotlinx.datetime.Clock

@Mockable
internal interface ServerTimeHandler {
    fun computeTimeOffset(serverTime: Long)
    fun toServerTimestamp(localTimestamp: Long = Clock.System.now().epochSeconds): Long
}

internal class ServerTimeHandlerImpl : ServerTimeHandler {

    /**
     * Used to store the difference (offset) between the server time and the local client time.
     * And it will be used to adjust timestamps between server and client times.
     */
    private var timeOffset: Long = 0

    /**
     * Compute the time offset between the server and the client
     * @param serverTime the server time to compute the offset
     */
    override fun computeTimeOffset(serverTime: Long) {
        kaliumLogger.i("ServerTimeHandler: computing time offset between server and client..")
        val offset = Clock.System.now().epochSeconds - serverTime
        timeOffset = offset
    }

    /**
     * Convert local timestamp to server timestamp
     * @param localTimestamp timestamp from client to convert
     * @return the timestamp adjusted with the client/server time shift
     */
    override fun toServerTimestamp(localTimestamp: Long): Long {
        return localTimestamp - timeOffset
    }
}
