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

package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallMetadataProfile
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class OnCloseCallTest {

    @Mock
    private val callRepository: CallRepository = configure(mock(CallRepository::class)) {
        stubsUnitByDefault = true
    }
    @Mock
    private val qualifiedIdMapper = mock(classOf<QualifiedIdMapper>())

    private lateinit var onCloseCall: OnCloseCall

    private val testScope = TestScope()

    @BeforeTest
    fun setUp() {
        onCloseCall = OnCloseCall(
            callRepository = callRepository,
            scope = testScope,
            qualifiedIdMapper = qualifiedIdMapper
        )
        given(qualifiedIdMapper).invocation { fromStringToQualifiedID(conversationIdString) }
            .then { conversationId }
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAConversationWithAnOngoingCall_whenClosingTheCallAndTheCallIsStillOngoing_thenVerifyTheStatusIsOngoing() = testScope.runTest {
        // given
        given(callRepository)
            .function(callRepository::getCallMetadataProfile)
            .whenInvoked()
            .thenReturn(
                CallMetadataProfile(mapOf(conversationIdString to callMetadata))
            )
        // when
        onCloseCall.onClosedCall(
            reason = 7,
            conversationId = conversationIdString,
            messageTime = Uint32_t(value = 1),
            userId = userId,
            clientId = clientId,
            arg = null
        )
        advanceUntilIdle()

        // then
        verify(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .with(eq(conversationIdString), eq(CallStatus.STILL_ONGOING))
            .wasInvoked(once)
    }

    @Suppress("FunctionNaming")
    @Test
    fun givenAConversationWithoutAnOngoingCall_whenClosingTheCallAndTheCallIsNotOngoing_thenVerifyTheStatusIsClosed() = testScope.runTest {
        // given
        // when
        onCloseCall.onClosedCall(
            reason = 4,
            conversationId = conversationIdString,
            messageTime = Uint32_t(value = 1),
            userId = userId,
            clientId = clientId,
            arg = null
        )
        advanceUntilIdle()

        // then
        verify(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .with(eq(conversationIdString), eq(CallStatus.MISSED))
            .wasInvoked(once)

        verify(callRepository)
            .suspendFunction(callRepository::persistMissedCall)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenAMMissedGroupCall_whenOnCloseCallbackOccurred_thenPersistMissedCall() = testScope.runTest {
        given(callRepository)
            .function(callRepository::getCallMetadataProfile)
            .whenInvoked()
            .thenReturn(
                CallMetadataProfile(mapOf(conversationIdString to callMetadata))
            )

        onCloseCall.onClosedCall(
            reason = 0,
            conversationId = conversationIdString,
            messageTime = Uint32_t(value = 1),
            userId = userId,
            clientId = clientId,
            arg = null
        )
        advanceUntilIdle()

        verify(callRepository)
            .suspendFunction(callRepository::updateCallStatusById)
            .with(eq(conversationIdString), eq(CallStatus.CLOSED))
            .wasInvoked(once)

        verify(callRepository)
            .suspendFunction(callRepository::persistMissedCall)
            .with(any())
            .wasInvoked(once)
    }

    companion object {
        private const val conversationIdString = "conversationId@domainId"
        private const val userId = "userId@domainId"
        private val conversationId = QualifiedID("conversationId", "domainId")
        private const val clientId = "clientId"
        private val callMetadata = CallMetadata(
            isMuted = false,
            isCameraOn = false,
            conversationName = null,
            conversationType = Conversation.Type.GROUP,
            callerName = null,
            callerTeamName = null,
            establishedTime = null,
            protocol = Conversation.ProtocolInfo.Proteus
        )
    }
}
