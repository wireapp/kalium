package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

/* This will be called when the calling system is ready for calling.
* The version parameter specifies the config obtained version to use
* for calling.
*/
fun interface ReadyHandler : Callback {
    fun onReady(version: Int, arg: Pointer?)
}
