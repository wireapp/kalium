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
    fun givenAVideoStateIsPauseOrStopped_whenCheckingIfVideoEnabled_thenReturnFalse() {
        val stoppedState = VideoStateCalling.STOPPED
        val pausedState = VideoStateCalling.PAUSED
        val unknownState = VideoStateCalling.UNKNOWN

        val result1 = videoStateChecker.isCameraOn(stoppedState)
        val result2 = videoStateChecker.isCameraOn(pausedState)
        val result3 = videoStateChecker.isCameraOn(unknownState)

        assertEquals(false, result1)
        assertEquals(false, result2)
        assertEquals(false, result3)
    }

    @Test
    fun givenAVideoStateIsStarted_whenCheckingIfVideoEnabled_thenReturnTrue() {
        val startedState = VideoStateCalling.STARTED
        val badConnectionState = VideoStateCalling.BAD_CONNECTION

        val result1 = videoStateChecker.isCameraOn(startedState)
        val result2 = videoStateChecker.isCameraOn(badConnectionState)

        assertEquals(true, result1)
        assertEquals(true, result2)
    }

    @Test
    fun givenAVideoStateSharing_whenCheckingScreenSharing_thenReturnTrue() {
        val screenShareState = VideoStateCalling.SCREENSHARE

        val result = videoStateChecker.isSharingScreen(screenShareState)

        assertEquals(true, result)
    }

    @Test
    fun givenAVideoStateUnknown_whenCheckingScreenSharing_thenReturnFalse() {
        val unknownState = VideoStateCalling.UNKNOWN

        val result = videoStateChecker.isSharingScreen(unknownState)

        assertEquals(false, result)
    }
}
