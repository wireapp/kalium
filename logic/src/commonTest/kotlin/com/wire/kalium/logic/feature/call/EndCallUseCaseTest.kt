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
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.thenDoNothing
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EndCallUseCaseTest {

    @Mock
    private val callManager = mock(classOf<CallManager>())

    @Mock
    private val callRepository = mock(classOf<CallRepository>())

    private lateinit var endCall: EndCallUseCase

    @BeforeTest
    fun setup() {
        endCall = EndCallUseCase(lazy { callManager }, callRepository)
    }

    @Test
    fun givenAnEstablishedCall_whenEndCallIsInvoked_thenInvokeEndCallOnce() = runTest {
        val conversationId = ConversationId("someone", "wire.com")

        given(callRepository)
            .suspendFunction(callRepository::establishedCallsFlow)
            .whenInvoked().then { flowOf(listOf(call)) }

        given(callManager)
            .suspendFunction(callManager::endCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        given(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .whenInvokedWith(eq(conversationId), eq(false))
            .thenDoNothing()

        endCall.invoke(conversationId)

        verify(callManager)
            .suspendFunction(callManager::endCall)
            .with(eq(conversationId))
            .wasInvoked(once)

        verify(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .with(eq(conversationId), eq(false))
            .wasInvoked(once)

        verify(callRepository)
            .function(callRepository::persistMissedCall)
            .with(eq(conversationId))
            .wasNotInvoked()
    }

    @Test
    fun givenNoEstablishedCall_whenEndCallIsInvoked_thenSaveMissedCall() = runTest {
        given(callRepository)
            .suspendFunction(callRepository::persistMissedCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        given(callRepository)
            .suspendFunction(callRepository::establishedCallsFlow)
            .whenInvoked().then { flowOf(listOf()) }

        given(callManager)
            .suspendFunction(callManager::endCall)
            .whenInvokedWith(eq(conversationId))
            .thenDoNothing()

        given(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .whenInvokedWith(eq(conversationId), eq(false))
            .thenDoNothing()

        endCall.invoke(conversationId)

        verify(callManager)
            .suspendFunction(callManager::endCall)
            .with(eq(conversationId))
            .wasInvoked(once)

        verify(callRepository)
            .function(callRepository::updateIsCameraOnById)
            .with(eq(conversationId), eq(false))
            .wasInvoked(once)

        verify(callRepository)
            .suspendFunction(callRepository::persistMissedCall)
            .with(any())
            .wasInvoked(once)
    }

    companion object {
        val conversationId = ConversationId("someone", "wire.com")
        val call = Call(
            conversationId = conversationId,
            status = CallStatus.ESTABLISHED,
            callerId = "called-id",
            isMuted = false,
            isCameraOn = false,
            conversationName = null,
            conversationType = Conversation.Type.GROUP,
            callerName = null,
            callerTeamName = null,
            establishedTime = null
        )
    }
}
