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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestCall
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.doesNothing
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UnMuteCallUseCaseTest {

    @Mock
    private val callManager = mock(CallManager::class)

    @Mock
    private val callRepository = mock(CallRepository::class)

    private lateinit var unMuteCall: UnMuteCallUseCase

    @BeforeTest
    fun setup() = runBlocking {
        unMuteCall = UnMuteCallUseCaseImpl(lazy { callManager }, callRepository)

        coEvery {
            callManager.muteCall(eq(isMuted))
        }.returns(Unit)

        every { callRepository.updateIsMutedById(eq(conversationId), eq(isMuted)) }
            .doesNothing()
    }

    @Test
    fun givenShouldApplyOnDeviceMicrophoneIsTrue_whenUnMuteUseCaseCalled_thenUpdateMuteStateAndUnMuteCall() = runTest {
        val shouldApplyOnDeviceMicrophone = true

        unMuteCall(conversationId, shouldApplyOnDeviceMicrophone)

        verify {
            callRepository.updateIsMutedById(eq(conversationId), eq(isMuted))
        }.wasInvoked(once)

        coVerify {
            callManager.muteCall(eq(isMuted))
        }.wasInvoked(once)
    }

    @Test
    fun givenShouldApplyOnDeviceMicrophoneIsFalse_whenUnMuteUseCaseCalled_thenUpdateMuteStateOnly() = runTest {
        val shouldApplyOnDeviceMicrophone = false

        unMuteCall(conversationId, shouldApplyOnDeviceMicrophone)

        verify {
            callRepository.updateIsMutedById(eq(conversationId), eq(isMuted))
        }.wasInvoked(once)

        coVerify {
            callManager.muteCall(eq(isMuted))
        }.wasNotInvoked()
    }

    companion object {
        const val isMuted = false
        private val conversationId = ConversationId("value", "domain")
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = TestCall.CALLER_ID,
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.GROUP,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )
    }
}
