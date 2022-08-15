package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.VideoStateCalling

interface VideoStateChecker {
    fun isCameraOn(state: VideoStateCalling): Boolean
}

class VideoStateCheckerImpl : VideoStateChecker {

    override fun isCameraOn(state: VideoStateCalling) = when (state) {
        VideoStateCalling.PAUSED, VideoStateCalling.STOPPED -> false
        else -> true
    }
}
