/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCaseImpl
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MuteCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var muteCall: MuteCallUseCase

    @BeforeTest
    fun setup() {
        muteCall = MuteCallUseCaseImpl(lazy { callManager }, callRepository)

        given(callManager)
            .suspendFunction(callManager::muteCall)
            .whenInvokedWith(eq(isMuted))
            .thenDoNothing()

        given(callRepository)
            .function(callRepository::updateIsMutedById)
            .whenInvokedWith(eq(conversationId.toString()), eq(isMuted))
            .thenDoNothing()
    }

    @Test
    fun givenShouldApplyOnDeviceMicrophoneIsTrue_whenMuteUseCaseCalled_thenUpdateMuteStateAndMuteCall() = runTest {
        val shouldApplyOnDeviceMicrophone = true

        muteCall(conversationId, shouldApplyOnDeviceMicrophone)

        verify(callRepository)
            .function(callRepository::updateIsMutedById)
            .with(eq(conversationId), eq(isMuted))
            .wasInvoked(once)

        verify(callManager)
            .suspendFunction(callManager::muteCall)
            .with(eq(isMuted))
            .wasInvoked(once)
    }

    @Test
    fun givenShouldApplyOnDeviceMicrophoneIsFalse_whenMuteUseCaseCalled_thenUpdateMuteStateOnly() = runTest {
        val shouldApplyOnDeviceMicrophone = false

        muteCall(conversationId, shouldApplyOnDeviceMicrophone)

        verify(callRepository)
            .function(callRepository::updateIsMutedById)
            .with(eq(conversationId), eq(isMuted))
            .wasInvoked(once)

        verify(callManager)
            .suspendFunction(callManager::muteCall)
            .with(eq(isMuted))
            .wasNotInvoked()
    }

    companion object {
        const val isMuted = true
        private val conversationId = ConversationId("value", "domain")
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = "called-id",
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
