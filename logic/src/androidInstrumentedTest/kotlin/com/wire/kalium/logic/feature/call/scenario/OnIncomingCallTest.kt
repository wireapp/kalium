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
package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.ConversationTypeForCall
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.id.QualifiedIdMapperImpl
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OnIncomingCallTest {

    val testScope = TestScope()

    @Test
    fun givenNewIncomingCall_whenIncomingCall_thenCreateCallWithStatusIncoming() = testScope.runTest() {
        val (arrangement, callback) = Arrangement(testScope)
            .arrange()

        callback.onIncomingCall(
            conversationId = TestConversation.CONVERSATION.id.toString(),
            messageTime = Uint32_t(value = 1),
            userId = TestUser.USER_ID.toString(),
            clientId = TestClient.CLIENT_ID.value,
            isVideoCall = false,
            shouldRing = true,
            conversationType = ConversationTypeCalling.Conference.avsValue,
            arg = null
        )
        advanceUntilIdle()

        verify(arrangement.callRepository)
            .suspendFunction(arrangement.callRepository::createCall)
            .with(
                eq(TestConversation.CONVERSATION.id),
                eq(ConversationTypeForCall.Conference),
                eq(CallStatus.INCOMING),
                eq(TestUser.USER_ID.toString()),
                eq(true),
                eq(false),
                eq(false)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenOngoingCall_whenIncomingCall_thenCreateCallWithStatusStillOngoing() = testScope.runTest() {
        val (arrangement, callback) = Arrangement(testScope)
            .arrange()

        callback.onIncomingCall(
            conversationId = TestConversation.CONVERSATION.id.toString(),
            messageTime = Uint32_t(value = 1),
            userId = TestUser.USER_ID.toString(),
            clientId = TestClient.CLIENT_ID.value,
            isVideoCall = false,
            shouldRing = false,
            conversationType = ConversationTypeCalling.Conference.avsValue,
            arg = null
        )
        advanceUntilIdle()

        verify(arrangement.callRepository)
            .suspendFunction(arrangement.callRepository::createCall)
            .with(
                eq(TestConversation.CONVERSATION.id),
                eq(ConversationTypeForCall.Conference),
                eq(CallStatus.STILL_ONGOING),
                eq(TestUser.USER_ID.toString()),
                eq(true),
                eq(false),
                eq(false)
            )
            .wasInvoked(exactly = once)
    }

    private class Arrangement(val testScope: TestScope) {

        @Mock
        val callRepository: CallRepository = configure(mock(CallRepository::class)) {
            stubsUnitByDefault = true
        }

        val kaliumConfigs = KaliumConfigs()

        val qualifiedIdMapper = QualifiedIdMapperImpl(TestUser.SELF.id)

        val callMapper = CallMapperImpl(qualifiedIdMapper)

        fun arrange() = this to OnIncomingCall(
            callRepository = callRepository,
            callMapper = callMapper,
            scope = testScope,
            qualifiedIdMapper = qualifiedIdMapper,
            kaliumConfigs = kaliumConfigs
        )
    }
}
