package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

/* Call established (with media) */
fun interface EstablishedCallHandler : Callback {
    fun onEstablishedCall(conversationId: String, userId: String, clientId: String, arg: Pointer?)
}
