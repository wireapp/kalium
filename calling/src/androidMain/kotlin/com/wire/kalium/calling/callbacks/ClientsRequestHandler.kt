package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.wire.kalium.calling.types.Handle

interface ClientsRequestHandler : Callback {
    fun onClientsRequest(inst: Handle, conversationId: String, arg: Pointer?)
}
