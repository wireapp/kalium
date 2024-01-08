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

package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.wire.kalium.calling.types.Uint32_t

/**
 * REASON_NORMAL             = 0
 * REASON_ERROR              = 1
 * REASON_TIMEOUT            = 2
 * REASON_LOST_MEDIA         = 3
 * REASON_CANCELED           = 4
 * REASON_ANSWERED_ELSEWHERE = 5
 * REASON_IO_ERROR           = 6
 * REASON_STILL_ONGOING      = 7
 * REASON_TIMEOUT_ECONN      = 8
 * REASON_DATACHANNEL        = 9
 * REASON_REJECTED           = 10
 * REASON_OUTDATED_CLIENT    = 11
 * REASON_NOONE_JOINED       = 12
 * REASON_EVERYONE_LEFT      = 13
 */
@Suppress("LongParameterList")
fun interface CloseCallHandler : Callback {
    fun onClosedCall(
        reason: Int,
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String?,
        arg: Pointer?
    )
}
