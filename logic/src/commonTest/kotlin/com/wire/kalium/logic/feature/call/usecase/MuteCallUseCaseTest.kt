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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MuteCallUseCaseTest {

        private val callManager = mock<CallManager>(mode = MockMode.autoUnit)

        private val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)

    private lateinit var muteCall: MuteCallUseCase

    @BeforeTest
    fun setup() = runBlocking {
        muteCall = MuteCallUseCaseImpl(lazy { callManager }, callRepository)

        everySuspend {
            callManager.muteCall(eq(isMuted))
        } returns (Unit)

        every {
            callRepository.updateIsMutedById(
                eq(conversationId),
                eq(isMuted)
            )
        } returns (Unit)
    }

    @Test
    fun givenShouldApplyOnDeviceMicrophoneIsTrue_whenMuteUseCaseCalled_thenUpdateMuteStateAndMuteCall() = runTest {
        val shouldApplyOnDeviceMicrophone = true

        muteCall(conversationId, shouldApplyOnDeviceMicrophone)

        verify(VerifyMode.exactly(1)) {
            callRepository.updateIsMutedById(eq(conversationId), eq(isMuted))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            callManager.muteCall(eq(isMuted))
        }
    }

    @Test
    fun givenShouldApplyOnDeviceMicrophoneIsFalse_whenMuteUseCaseCalled_thenUpdateMuteStateOnly() = runTest {
        val shouldApplyOnDeviceMicrophone = false

        muteCall(conversationId, shouldApplyOnDeviceMicrophone)

        verify(VerifyMode.exactly(1)) {
            callRepository.updateIsMutedById(eq(conversationId), eq(isMuted))
        }

        verifySuspend(VerifyMode.not) {
            callManager.muteCall(eq(isMuted))
        }
    }

    companion object {
        const val isMuted = true
        private val conversationId = ConversationId("value", "domain")
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = TestCall.CALLER_ID,
            isMuted = false,
            isCameraOn = false,
            isCbrEnabled = false,
            conversationName = null,
            conversationType = Conversation.Type.Group.Regular,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )
    }
}
