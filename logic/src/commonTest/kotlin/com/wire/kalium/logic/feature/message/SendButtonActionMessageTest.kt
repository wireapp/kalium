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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.composite.SendButtonActionMessageUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangement
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangementImpl
import com.wire.kalium.logic.util.arrangement.SyncManagerArrangement
import com.wire.kalium.logic.util.arrangement.SyncManagerArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageMetaDataRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageMetaDataRepositoryArrangementImpl
import io.mockative.any
import io.mockative.matching
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SendButtonActionMessageTest {

    @Test
    fun givenSyncFailed_thenReturnError() = runTest {
        val convId = ConversationId("conversation-id", "conversation-domain")
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withWaitUntilLiveOrFailure(Either.Left(StorageFailure.DataNotFound))
            }

        val result = useCase(
            conversationId = convId,
            messageId = "message-id",
            buttonId = "button-id",
        )

        assertIs<SendButtonActionMessageUseCase.Result.Failure>(result)

        verify(arrangement.messageMetaDataRepository)
            .suspendFunction(arrangement.messageMetaDataRepository::originalSenderId)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any(), any())
            .wasNotInvoked()

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasNotInvoked()

        verify(arrangement.syncManager)
            .suspendFunction(arrangement.syncManager::waitUntilLiveOrFailure)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMessageSendingSuccess_thenReturnSuccess() = runTest {
        val convId = ConversationId("conversation-id", "conversation-domain")
        val originalSender = UserId("original-sender-id", "original-sender-domain")
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withWaitUntilLiveOrFailure(Either.Right(Unit))
                withCurrentClientIdSuccess(ClientId("client-id"))
                withMessageOriginalSender(Either.Right(originalSender))
                withSendMessageSucceed()
            }

        val result = useCase(
            conversationId = convId,
            messageId = "message-id",
            buttonId = "button-id",
        )

        assertIs<SendButtonActionMessageUseCase.Result.Success>(result)

        verify(arrangement.messageMetaDataRepository)
            .suspendFunction(arrangement.messageMetaDataRepository::originalSenderId)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncManager)
            .suspendFunction(arrangement.syncManager::waitUntilLiveOrFailure)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMessageSendingSuccess_thenMessageIsSentOnlyToOriginalSender() = runTest {
        val convId = ConversationId("conversation-id", "conversation-domain")
        val originalSender = UserId("original-sender-id", "original-sender-domain")
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withWaitUntilLiveOrFailure(Either.Right(Unit))
                withCurrentClientIdSuccess(ClientId("client-id"))
                withMessageOriginalSender(Either.Right(originalSender))
                withSendMessageSucceed()
            }

        val result = useCase(
            conversationId = convId,
            messageId = "message-id",
            buttonId = "button-id",
        )

        assertIs<SendButtonActionMessageUseCase.Result.Success>(result)

        verify(arrangement.messageMetaDataRepository)
            .suspendFunction(arrangement.messageMetaDataRepository::originalSenderId)
            .with(any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any(), matching {
                it is MessageTarget.Users && it.userId == listOf(originalSender)
            })
            .wasInvoked(exactly = once)

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasInvoked(exactly = once)

        verify(arrangement.syncManager)
            .suspendFunction(arrangement.syncManager::waitUntilLiveOrFailure)
            .wasInvoked(exactly = once)
    }

    private companion object {
        val SELF_USER_ID: UserId = UserId("self-user-id", "self-user-domain")
    }

    private class Arrangement :
        MessageMetaDataRepositoryArrangement by MessageMetaDataRepositoryArrangementImpl(),
        MessageSenderArrangement by MessageSenderArrangementImpl(),
        SyncManagerArrangement by SyncManagerArrangementImpl(),
        CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl() {

        private lateinit var useCase: SendButtonActionMessageUseCase

        fun arrange(block: Arrangement.() -> Unit): Pair<Arrangement, SendButtonActionMessageUseCase> {
            apply(block)
            useCase = SendButtonActionMessageUseCase(
                messageMetaDataRepository = messageMetaDataRepository,
                messageSender = messageSender,
                syncManager = syncManager,
                currentClientIdProvider = currentClientIdProvider,
                selfUserId = SELF_USER_ID,
            )

            return this to useCase
        }
    }
}
