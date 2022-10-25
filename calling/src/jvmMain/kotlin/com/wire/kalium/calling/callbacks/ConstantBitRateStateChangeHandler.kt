package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

fun interface ConstantBitRateStateChangeHandler : Callback {
    fun onBitRateStateChanged(
        userId: String,
        clientId: String,
        isEnabled: Boolean,
        arg: Pointer?
    )
}
