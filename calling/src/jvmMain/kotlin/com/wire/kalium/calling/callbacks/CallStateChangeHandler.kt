package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

interface CallStateChangeHandler : Callback {
    fun onCallStateChanged(conversationId: String, state: Int, arg: Pointer?)
}
