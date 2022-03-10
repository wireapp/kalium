package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

interface AnsweredCallHandler : Callback {
    /**
     * Note, only relevant for one-to-one calls
     */
    fun onAnsweredCall(conversationId: String, arg: Pointer?)
}
