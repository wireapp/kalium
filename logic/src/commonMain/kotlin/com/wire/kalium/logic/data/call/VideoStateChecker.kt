package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.VideoStateCalling

interface VideoStateChecker {
    fun isCameraOn(state: VideoStateCalling): Boolean
    fun isSharingScreen(state: VideoStateCalling): Boolean
}

class VideoStateCheckerImpl : VideoStateChecker {

    override fun isCameraOn(state: VideoStateCalling) = when (state) {
        VideoStateCalling.STARTED, VideoStateCalling.BAD_CONNECTION -> true
        else -> false
    }

    override fun isSharingScreen(state: VideoStateCalling) = state == VideoStateCalling.SCREENSHARE
}
