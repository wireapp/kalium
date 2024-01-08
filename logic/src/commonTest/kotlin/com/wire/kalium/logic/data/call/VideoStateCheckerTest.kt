/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
