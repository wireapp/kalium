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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.setLegalHoldRequest(
                eq(legalHoldRequestSelfUser.clientId.value),
                eq(legalHoldRequestSelfUser.lastPreKey.id),
                eq(legalHoldRequestSelfUser.lastPreKey.key)
            )
        }
    }

    @Test
    fun givenLegalHoldRequestEvent_whenUserIdIsNotIsSelfUser_thenIgnoreEvent() = runTest {
        val (arrangement, handler) = Arrangement()
            .arrange()

        val result = handler.handle(legalHoldRequestOtherUser)

        result.shouldSucceed()
        verifySuspend(VerifyMode.not) {
            arrangement.userConfigRepository.setLegalHoldRequest(any(), any(), any())
        }
    }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock()

        fun arrange() =
            this to LegalHoldRequestHandlerImpl(TestUser.SELF.id, userConfigRepository)

        suspend fun withSetLegalHoldSuccess() = apply {
            everySuspend {
                userConfigRepository.setLegalHoldRequest(any(), any(), any())
            } returns Either.Right(Unit)
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
