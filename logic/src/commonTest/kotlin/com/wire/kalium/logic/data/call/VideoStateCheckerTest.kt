package com.wire.kalium.logic.data.call

import com.wire.kalium.calling.VideoStateCalling
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoStateCheckerTest {

    lateinit var videoStateChecker: VideoStateChecker

    @BeforeTest
    fun setUp() {
        videoStateChecker = VideoStateCheckerImpl()
    }

    @Test
    fun givenAVideoStateIsPauseOrStoppedWhenCheckingIfVideoEnabledThenReturnFalse() {
        val stoppedState = VideoStateCalling.STOPPED
        val pausedState = VideoStateCalling.PAUSED

        val result1 = videoStateChecker.isCameraOn(stoppedState)
        val result2 = videoStateChecker.isCameraOn(pausedState)

        assertEquals(false, result1)
        assertEquals(false, result2)
    }

    @Test
    fun givenAVideoStateIsStartedWhenCheckingIfVideoEnabledThenReturnTrue() {
        val startedState = VideoStateCalling.STARTED
        val badConnectionState = VideoStateCalling.BAD_CONNECTION
        val screenShareState = VideoStateCalling.SCREENSHARE

        val result1 = videoStateChecker.isCameraOn(startedState)
        val result2 = videoStateChecker.isCameraOn(badConnectionState)
        val result3 = videoStateChecker.isCameraOn(screenShareState)

        assertEquals(true, result1)
        assertEquals(true, result2)
        assertEquals(true, result3)
    }
}
