package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.wire.kalium.calling.types.Uint32_t

fun interface MissedCallHandler : Callback {
    fun onMissedCall(
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        isVideoCall: Boolean,
        arg: Pointer?
    )
}
