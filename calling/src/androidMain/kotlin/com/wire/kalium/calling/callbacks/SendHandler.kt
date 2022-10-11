package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.wire.kalium.calling.types.Size_t

/* Send calling message otr data */
fun interface SendHandler : Callback {
    @Suppress("LongParameterList")
    fun onSend(
        context: Pointer?,
        conversationId: String,
        userIdSelf: String,
        clientIdSelf: String,
        targetRecipientsJson: String?,
        clientIdDestination: String?,
        data: Pointer?,
        length: Size_t,
        isTransient: Boolean,
        arg: Pointer?
    ): Int
}
