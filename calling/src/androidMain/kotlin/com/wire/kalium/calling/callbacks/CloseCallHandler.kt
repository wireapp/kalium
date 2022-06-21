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
