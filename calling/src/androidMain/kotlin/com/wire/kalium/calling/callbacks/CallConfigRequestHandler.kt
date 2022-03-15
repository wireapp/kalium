package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.wire.kalium.calling.types.Handle

interface CallConfigRequestHandler : Callback {
    fun onConfigRequest(inst: Handle, arg: Pointer?): Int
}
