package com.wire.kalium.calling

/**
 * NORMAL for audio call
 * VIDEO for video cal
 */
enum class CallType(val avsValue: Int) {
    NORMAL(avsValue = 0),
    VIDEO(avsValue = 1),
}
