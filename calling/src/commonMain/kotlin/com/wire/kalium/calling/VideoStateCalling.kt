package com.wire.kalium.calling

enum class VideoStateCalling(val avsValue: Int) {
    STOPPED(avsValue = 0),
    STARTED(avsValue = 1),
    BAD_CONNECTION(avsValue = 2),
    PAUSED(avsValue = 3),
    SCREENSHARE(avsValue = 4)
}
