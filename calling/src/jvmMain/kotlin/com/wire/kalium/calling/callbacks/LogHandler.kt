package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

/**
 * LEVEL_DEBUG 0
 * LEVEL_INFO  1
 * LEVEL_WARN  2
 * LEVEL_ERROR 3
 */
interface LogHandler : Callback {
    fun onLog(level: Int, message: String, arg: Pointer?)
}
