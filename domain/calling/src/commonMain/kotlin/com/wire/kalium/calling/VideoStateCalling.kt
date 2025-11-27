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

package com.wire.kalium.calling

enum class VideoStateCalling(val avsValue: Int) {
    STOPPED(avsValue = 0),
    STARTED(avsValue = 1),
    BAD_CONNECTION(avsValue = 2),
    PAUSED(avsValue = 3),
    SCREENSHARE(avsValue = 4),
    UNKNOWN(avsValue = 5) // Internal
}
