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
package com.wire.kalium.logic.data.message.ephemeral

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangement
import com.wire.kalium.logic.util.arrangement.MessageSenderArrangementImpl
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.SelfConversationIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.AssetRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.AssetRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MessageRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coVerify
import io.mockative.matchers.EqualsMatcher
import io.mockative.matches
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class DeleteEphemeralMessageForSelfUserAsReceiverUseCaseTest {

    @Test
    fun givenMessage_whenDeleting_then2DeleteMessagesAreSentForSelfAndOriginalSender() = runTest {
        val message = MESSAGE_REGULAR
        val messageId = message.id
        val conversationId = message.conversationId
        val senderUserId = message.senderUserId
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withMarkAsDeleted(Either.Right(Unit))
                withCurrentClientIdSuccess(CURRENT_CLIENT_ID)
                withSelfConversationIds(SELF_CONVERSATION_ID)
                withGetMessageById(Either.Right(message))
                withSendMessageSucceed()
                withDeleteMessage(Either.Right(Unit))
            }

        useCase(conversationId, messageId).shouldSucceed()

        coVerify {
            arrangement.messageRepository.markMessageAsDeleted(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageSender.sendMessage(
                matches {
                    it.conversationId == SELF_CONVERSATION_ID.first() &&
                            it.content == MessageContent.DeleteForMe(messageId, conversationId)
                },
                matches {
                    it == MessageTarget.Conversation()
                }
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageSender.sendMessage(
                matches {
                    it.conversationId == conversationId &&
                            it.content == MessageContent.DeleteMessage(messageId)
                },
                matches {
                    it == MessageTarget.Users(listOf(senderUserId))
                }
            )
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageRepository.deleteMessage(any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAssetMessage_whenDeleting_thenDeleteAssetLocally() = runTest {
        val assetContent = ASSET_IMAGE_CONTENT
        val message = MESSAGE_REGULAR.copy(
            content = MessageContent.Asset(assetContent)
        )
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(CURRENT_CLIENT_ID)
                withSelfConversationIds(SELF_CONVERSATION_ID)
                withSendMessageSucceed()
                withDeleteMessage(Either.Right(Unit))
                withMarkAsDeleted(Either.Right(Unit), EqualsMatcher(message.id), EqualsMatcher(message.conversationId))
                withGetMessageById(Either.Right(message), EqualsMatcher(message.id), EqualsMatcher(message.conversationId))
                withDeleteAssetLocally(Either.Right(Unit), EqualsMatcher(assetContent.remoteData.assetId))
            }

        useCase(message.conversationId, message.id).shouldSucceed()

        coVerify {
            arrangement.assetRepository.deleteAssetLocally(assetContent.remoteData.assetId)
        }.wasInvoked(exactly = once)
    }

    private companion object {
        val SELF_USER_ID = UserId("selfUserId", "selfUserDomain.sy")
        val SENDER_USER_ID = UserId("senderUserId", "senderDomain")
        val SELF_CONVERSATION_ID = listOf(ConversationId("selfConversationId", "selfConversationDomain.com"))
        val CURRENT_CLIENT_ID = ClientId("currentClientId")
        val ASSET_CONTENT_REMOTE_DATA = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(16),
            assetId = "asset-id",
            assetToken = "==some-asset-token",
            assetDomain = "some-asset-domain.com",
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
        )
        val ASSET_IMAGE_CONTENT = AssetContent(
            0L,
            "name",
            "image/jpg",
            AssetContent.AssetMetadata.Image(100, 100),
            ASSET_CONTENT_REMOTE_DATA
        )
        val MESSAGE_REGULAR = Message.Regular(
            id = "messageId",
            content = MessageContent.Text("text"),
            conversationId = ConversationId("conversationId", "conversationDomain"),
            date = Instant.DISTANT_FUTURE,
            senderUserId = SENDER_USER_ID,
            senderClientId = CURRENT_CLIENT_ID,
            status = Message.Status.Pending,
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = true
        )
    }

    private class Arrangement :
        CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl(),
        MessageRepositoryArrangement by MessageRepositoryArrangementImpl(),
        MessageSenderArrangement by MessageSenderArrangementImpl(),
        SelfConversationIdProviderArrangement by SelfConversationIdProviderArrangementImpl(),
        AssetRepositoryArrangement by AssetRepositoryArrangementImpl() {

        private val useCase: DeleteEphemeralMessageForSelfUserAsReceiverUseCase =
            DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
                messageRepository = messageRepository,
                messageSender = messageSender,
                selfUserId = SELF_USER_ID,
                selfConversationIdProvider = selfConversationIdProvider,
                assetRepository = assetRepository,
                currentClientIdProvider = currentClientIdProvider
            )

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeleteEphemeralMessageForSelfUserAsReceiverUseCase> {
            block()
            return this to useCase
        }
    }
}
