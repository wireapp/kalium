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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangement
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangementImpl
import com.wire.kalium.logic.util.arrangement.SyncManagerArrangement
import com.wire.kalium.logic.util.arrangement.SyncManagerArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.matches
import io.mockative.once
import kotlinx.coroutines.runBlocking
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

        coVerify {
            arrangement.messageSender.sendMessage(any(), matches {
                it is MessageTarget.Users && it.userId == listOf(buttonActionSender)
            })
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.syncManager.waitUntilLiveOrFailure()
        }.wasInvoked(exactly = once)
    }

    private companion object {
        val SELF_USER_ID: UserId = UserId("self-user-id", "self-user-domain")
    }

    private class Arrangement :
        MessageSenderArrangement by MessageSenderArrangementImpl(),
        SyncManagerArrangement by SyncManagerArrangementImpl(),
        CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl() {

        private lateinit var useCase: SendButtonActionConfirmationMessageUseCase

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, SendButtonActionConfirmationMessageUseCase> {
            runBlocking { block() }
            useCase = SendButtonActionConfirmationMessageUseCase(
                messageSender = messageSender,
                syncManager = syncManager,
                currentClientIdProvider = currentClientIdProvider,
                selfUserId = SELF_USER_ID,
            )

            return this to useCase
        }
    }
}
