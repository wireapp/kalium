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

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.framework.TestCall
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.eq
import dev.mokkery.everySuspend
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UpdateVideoStateUseCaseTest {

        private val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

    private lateinit var updateVideoStateUseCase: UpdateVideoStateUseCase

    @BeforeTest
    fun setup() {
        updateVideoStateUseCase = UpdateVideoStateUseCase(callRepository)
        every { callRepository.updateIsCameraOnById(eq(conversationId), eq(isCameraOn)) } returns (Unit)
    }

    @Test
    fun givenAFlowOfEstablishedCallsThatContainsAnEstablishedCall_whenUseCaseInvoked_thenInvokeUpdateVideoState() = runTest {
        val establishedCall = Call(
            conversationId,
            CallStatus.ESTABLISHED,
            isMuted = true,
            isCameraOn = true,
            isCbrEnabled = false,
            callerId = TestCall.CALLER_ID,
            conversationName = "",
            Conversation.Type.OneOnOne,
            null,
            null
        )

        everySuspend {
            callRepository.establishedCallsFlow()
        } returns (flowOf(listOf(establishedCall)))

        updateVideoStateUseCase(conversationId, videoState)

        verify(VerifyMode.exactly(1)) {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(isCameraOn))
        }
    }

    @Test
    fun givenAFlowOfEstablishedCallsThatContainsNonEstablishedCall_whenUseCaseInvoked_thenDoNotInvokeUpdateVideoState() = runTest {
        everySuspend {
            callRepository.establishedCallsFlow()
        } returns (flowOf(listOf()))

        updateVideoStateUseCase(conversationId, videoState)

        verify(VerifyMode.exactly(1)) {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(isCameraOn))
        }
    }

    companion object {
        private const val isCameraOn = true
        private val videoState = VideoState.STARTED
        private val conversationId = ConversationId("value", "domain")
    }

}
