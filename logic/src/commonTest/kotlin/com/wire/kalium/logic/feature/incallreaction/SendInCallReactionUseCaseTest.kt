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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SendInCallReactionUseCaseTest {

    @Test
    fun givenEstablishedConnection_WhenSending_ShouldReturnSuccess() = runTest {

        val (arrangement, sendReactionUseCase) = Arrangement(this)
            .withCurrentClientProviderSuccess()
            .withSendMessageSuccess()
            .arrange()

        val result = sendReactionUseCase(ConversationId("id", "domain"), "reaction")

        assertIs<MessageOperationResult.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenNoConnectionWhenSendingShouldFail() = runTest {

        val (arrangement, sendReactionUseCase) = Arrangement(this)
            .withCurrentClientProviderSuccess()
            .withSendMessageFailure()
            .arrange()

        val result = sendReactionUseCase(ConversationId("id", "domain"), "reaction")

        assertIs<MessageOperationResult.Failure>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    private class Arrangement(private val coroutineScope: CoroutineScope) {

        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)

        suspend fun withSendMessageSuccess() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withSendMessageFailure() = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        }

        suspend fun withCurrentClientProviderSuccess(clientId: ClientId = TestClient.CLIENT_ID) = apply {
            everySuspend {
                currentClientIdProvider.invoke()
            } returns Either.Right(clientId)
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
