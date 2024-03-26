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
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LegalHoldRequestHandlerTest {

    @Test
    fun givenLegalHoldRequestEvent_whenUserIdIsSelfUser_thenStoreRequestLocally() = runTest {
        val (arrangement, handler) = Arrangement()
            .withSetLegalHoldSuccess()
            .arrange()

        val result = handler.handle(legalHoldRequestSelfUser)

        result.shouldSucceed()
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldRequest)
            .with(
                eq(legalHoldRequestSelfUser.clientId.value),
                eq(legalHoldRequestSelfUser.lastPreKey.id),
                eq(legalHoldRequestSelfUser.lastPreKey.key)
            )
            .wasInvoked(once)
    }

    @Test
    fun givenLegalHoldRequestEvent_whenUserIdIsNotIsSelfUser_thenIgnoreEvent() = runTest {
        val (arrangement, handler) = Arrangement()
            .arrange()

        val result = handler.handle(legalHoldRequestOtherUser)

        result.shouldSucceed()
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldRequest)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        fun arrange() =
            this to LegalHoldRequestHandlerImpl(TestUser.SELF.id, userConfigRepository)

        fun withSetLegalHoldSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setLegalHoldRequest)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(Unit))
        }
    }

    companion object {
        private val legalHoldRequestSelfUser = Event.User.LegalHoldRequest(
            id = "event-id",
            clientId = ClientId("client-id"),
            lastPreKey = LastPreKey(3, "key"),
            userId = TestUser.SELF.id
        )
        private val legalHoldRequestOtherUser = Event.User.LegalHoldRequest(
            id = "event-id",
            clientId = ClientId("client-id"),
            lastPreKey = LastPreKey(3, "key"),
            userId = TestUser.OTHER_USER_ID
        )
    }
}
