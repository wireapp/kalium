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
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.doesNothing
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UpdateVideoStateUseCaseTest {

        private val callRepository = mock(CallRepository::class)

    private lateinit var updateVideoStateUseCase: UpdateVideoStateUseCase

    @BeforeTest
    fun setup() {
        updateVideoStateUseCase = UpdateVideoStateUseCase(callRepository)
        every { callRepository.updateIsCameraOnById(eq(conversationId), eq(isCameraOn)) }
            .doesNothing()
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

        coEvery {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf(establishedCall)))

        updateVideoStateUseCase(conversationId, videoState)

        verify {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(isCameraOn))
        }.wasInvoked(once)
    }

    @Test
    fun givenAFlowOfEstablishedCallsThatContainsNonEstablishedCall_whenUseCaseInvoked_thenDoNotInvokeUpdateVideoState() = runTest {
        coEvery {
            callRepository.establishedCallsFlow()
        }.returns(flowOf(listOf()))

        updateVideoStateUseCase(conversationId, videoState)

        verify {
            callRepository.updateIsCameraOnById(eq(conversationId), eq(isCameraOn))
        }.wasInvoked(once)
    }

    companion object {
        private const val isCameraOn = true
        private val videoState = VideoState.STARTED
        private val conversationId = ConversationId("value", "domain")
    }

}
