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
package com.wire.kalium.logic.feature.message.composite

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.logic.sync.SyncManager
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SendButtonActionConfirmationMessageTest {

    @Test
    fun givenMessageSendingSuccess_thenMessageIsSentOnlyToOriginalSenderOfTheButtonAction() = runTest {
        val convId = ConversationId("conversation-id", "conversation-domain")
        val buttonActionSender = UserId("action-sender-id", "action-sender-domain")
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withWaitUntilLiveOrFailure(Either.Right(Unit))
                withCurrentClientIdSuccess(ClientId("client-id"))
                withSendMessageSucceed()
            }

        val result = useCase(
            conversationId = convId,
            messageId = "message-id",
            buttonId = "button-id",
            userIds = listOf(buttonActionSender)
        )

        assertIs<SendButtonActionConfirmationMessageUseCase.Result.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), matching {
                it is MessageTarget.Users && it.userId == listOf(buttonActionSender)
            })
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.currentClientIdProvider.invoke()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }
    }

    private companion object {
        val SELF_USER_ID: UserId = UserId("self-user-id", "self-user-domain")
    }

    private class Arrangement {
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val syncManager = mock<SyncManager>(mode = MockMode.autoUnit)
        val currentClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)

        private lateinit var useCase: SendButtonActionConfirmationMessageUseCase

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, SendButtonActionConfirmationMessageUseCase> {
            block()
            useCase = SendButtonActionConfirmationMessageUseCase(
                messageSender = messageSender,
                syncManager = syncManager,
                currentClientIdProvider = currentClientIdProvider,
                selfUserId = SELF_USER_ID,
            )

            return this to useCase
        }

        suspend fun withWaitUntilLiveOrFailure(result: Either<com.wire.kalium.common.error.CoreFailure, Unit>) {
            everySuspend { syncManager.waitUntilLiveOrFailure() } returns result
        }

        suspend fun withCurrentClientIdSuccess(currentClientId: ClientId) {
            everySuspend { currentClientIdProvider.invoke() } returns Either.Right(currentClientId)
        }

        suspend fun withSendMessageSucceed() {
            everySuspend { messageSender.sendMessage(any(), any()) } returns Either.Right(Unit)
        }
    }
}
