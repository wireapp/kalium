package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.wire.kalium.calling.types.Handle

interface ActiveSpeakersHandler : Callback {
    fun onActiveSpeakersChanged(inst: Handle, conversationId: String, data: String, arg: Pointer?)
}
