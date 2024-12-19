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
package com.wire.kalium.logic.feature.incallreaction

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SendInCallReactionUseCaseTest {

    @Mock
    val messageSender = mock(MessageSender::class)

    @Test
    fun givenEstablishedConnection_WhenSending_ShouldReturnSuccess() = runTest {

        // Given
        val (arrangement, sendReactionUseCase) = Arrangement(this)
            .withCurrentClientProviderSuccess()
            .withSendMessageSuccess()
            .arrange()

        // When
        val result = sendReactionUseCase(ConversationId("id", "domain"), "reaction")

        // Then
        result.shouldSucceed()

        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenNoConnectionWhenSendingShouldFail() = runTest {

        // Given
        val (arrangement, sendReactionUseCase) = Arrangement(this)
            .withCurrentClientProviderSuccess()
            .withSendMessageFailure()
            .arrange()

        // When
        val result = sendReactionUseCase(ConversationId("id", "domain"), "reaction")

        // Then
        result.shouldFail()

        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(once)
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {
        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        suspend fun withSendMessageSuccess() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withSendMessageFailure() = apply {
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }

        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        fun arrange() = this to SendInCallReactionUseCase(
            selfUserId = TestUser.SELF.id,
            provideClientId = currentClientIdProvider,
            messageSender = messageSender,
            dispatchers = coroutineScope.testKaliumDispatcher,
            scope = coroutineScope,
        )
    }
}
