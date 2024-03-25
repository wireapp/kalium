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
package com.wire.kalium.logic.feature.call.usecase.video

import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestConversation
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SetVideoSendStateUseCaseTest {

    @Test
    fun givenVideoState_whenRunningUsecase_thenInvokeSetVideoSendStateOnce() = runTest {
        val (arrangement, setVideoSendState) = Arrangement()
            .arrange()

        setVideoSendState.invoke(TestConversation.ID, VideoState.STARTED)

        verify(arrangement.callManager)
            .suspendFunction(arrangement.callManager::setVideoSendState)
            .with(any(), eq(VideoState.STARTED))
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val callManager = mock(classOf<CallManager>())

        val setVideoSendState = SetVideoSendStateUseCase(lazy { callManager })

        fun arrange() = this to setVideoSendState
    }
}
