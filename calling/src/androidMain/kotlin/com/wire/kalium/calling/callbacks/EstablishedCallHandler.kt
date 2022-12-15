package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

/* Call established (with media) */
fun interface EstablishedCallHandler : Callback {
    fun onEstablishedCall(remoteConversationId: String, userId: String, clientId: String, arg: Pointer?)
}
