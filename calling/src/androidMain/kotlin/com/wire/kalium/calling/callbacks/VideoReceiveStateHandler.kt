package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

/**
 *   VIDEO_STATE_STOPPED      = 0
 *   VIDEO_STATE_STARTED      = 1
 *   VIDEO_STATE_BAD_CONN     = 2
 *   VIDEO_STATE_PAUSED       = 3
 *   VIDEO_STATE_SCREENSHARE  = 4
 */
fun interface VideoReceiveStateHandler : Callback {
    fun onVideoReceiveStateChanged(
        conversationId: String,
        userId: String,
        clientId: String,
        state: Int,
        arg: Pointer?
    )
}
