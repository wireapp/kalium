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

enum class CallClosedReason(val avsValue: Int) {
    NORMAL(avsValue = 0),
    ERROR(avsValue = 1),
    TIMEOUT(avsValue = 2),
    LOST_MEDIA(avsValue = 3),
    CANCELLED(avsValue = 4),
    ANSWERED_ELSEWHERE(avsValue = 5),
    IOERROR(avsValue = 6),
    STILL_ONGOING(avsValue = 7),
    TIMEOUT_ECONN(avsValue = 8),
    DATA_CHANNEL(avsValue = 9),
    REJECTED(avsValue = 10),
    UNKNOWN(avsValue = -1);

    companion object {
        fun fromInt(value: Int) = values().firstOrNull {
            it.avsValue == value
        } ?: UNKNOWN
    }
}
