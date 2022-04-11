package com.wire.kalium.calling

/**
 * [AUDIO] for audio call
 * [VIDEO] for video cal
 */
enum class CallType(val avsValue: Int) {
    AUDIO(avsValue = 0),
    VIDEO(avsValue = 1),
}
