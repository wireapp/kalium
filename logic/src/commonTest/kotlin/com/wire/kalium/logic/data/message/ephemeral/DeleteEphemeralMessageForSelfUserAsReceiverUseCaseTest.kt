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
package com.wire.kalium.logic.data.message.ephemeral

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.AssetRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.AssetRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.CurrentClientIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangement
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangementImpl
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.any
import io.mockative.matching
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteEphemeralMessageForSelfUserAsReceiverUseCaseTest {

    @Test
    fun givenMessage_whenDeleting_then2DeleteMessagesAreSentForSelfAndOriginalSender() = runTest {
        val messageId = "messageId"
        val conversationId = ConversationId("conversationId", "conversationDomain.com")
        val currentClientId = CURRENT_CLIENT_ID

        val senderUserID = UserId("senderUserId", "senderUserDomain.com")
        val message = Message.Regular(
            id = messageId,
            content = MessageContent.Text("text"),
            conversationId = conversationId,
            date = Instant.DISTANT_FUTURE.toIsoDateTimeString(),
            senderUserId = senderUserID,
            senderClientId = currentClientId,
            status = Message.Status.PENDING,
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = true
        )
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(CURRENT_CLIENT_ID)
                withSelfConversationIds(SELF_CONVERSION_ID)
                withGetMessageById(Either.Right(message))
                withSendMessageSucceed()
                withDeleteMessage(Either.Right(Unit))
            }

        useCase(conversationId, messageId).shouldSucceed()

        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching {
                    it.conversationId == SELF_CONVERSION_ID.first() &&
                            it.content == MessageContent.DeleteForMe(messageId, conversationId)
                }, matching {
                    it == MessageTarget.Conversation()
                })
            .wasInvoked(exactly = once)


        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(
                matching {
                    it.conversationId == conversationId &&
                            it.content == MessageContent.DeleteMessage(messageId)
                }, matching {
                    it == MessageTarget.Users(listOf(senderUserID))
                })
            .wasInvoked(exactly = once)

        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::deleteMessage)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    private companion object {
        val selfUserId = UserId("selfUserId", "selfUserDomain.sy")
        val SELF_CONVERSION_ID = listOf(ConversationId("selfConversationId", "selfConversationDomain.com"))
        val CURRENT_CLIENT_ID = ClientId("currentClientId")
    }

    private class Arrangement
        : CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl(),
        MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        MessageSenderArrangement by MessageSenderArrangementImpl(),
        SelfConversationIdProviderArrangement by SelfConversationIdProviderArrangementImpl(),
        AssetRepositoryArrangement by AssetRepositoryArrangementImpl() {

        private val useCase: DeleteEphemeralMessageForSelfUserAsReceiverUseCase =
            DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
                messageRepository = messageRepository,
                messageSender = messageSender,
                selfUserId = selfUserId,
                selfConversationIdProvider = selfConversationIdProvider,
                assetRepository = assetRepository,
                currentClientIdProvider = currentClientIdProvider
            )

        fun arrange(block: Arrangement.() -> Unit): Pair<Arrangement, DeleteEphemeralMessageForSelfUserAsReceiverUseCase> {
            block()
            return this to useCase
        }
    }
}
