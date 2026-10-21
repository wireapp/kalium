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
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

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

        useCase(conversationId, messageId).toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.markMessageAsDeleted(any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matches {
                    it.conversationId == SELF_CONVERSATION_ID.first() &&
                            it.content == MessageContent.DeleteForMe(messageId, conversationId)
                },
                matches {
                    it == MessageTarget.Conversation()
                }
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matches {
                    it.conversationId == conversationId &&
                            it.content == MessageContent.DeleteMessage(messageId)
                },
                matches {
                    it == MessageTarget.Users(listOf(senderUserId))
                }
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.deleteMessage(any(), any())
        }
        verifySuspend(VerifyMode.not) { arrangement.assetRepository.deleteAssetLocally(any(), any()) }
    }

    @Test
    fun givenAssetMessage_whenDeleting_thenDeleteAssetLocally() = runTest {
        val preview = AssetContent.Preview(
            "image/jpeg", 123, 64, 48, ASSET_CONTENT_REMOTE_DATA.copy(assetId = "preview-id")
        )
        val assetContent = ASSET_IMAGE_CONTENT.copy(preview = preview)
        val message = MESSAGE_REGULAR.copy(
            content = MessageContent.Asset(assetContent)
        )
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(CURRENT_CLIENT_ID)
                withSelfConversationIds(SELF_CONVERSATION_ID)
                withSendMessageSucceed()
                withDeleteMessage(Either.Right(Unit))
                withMarkAsDeleted(Either.Right(Unit))
                withGetMessageById(Either.Right(message))
                withDeleteAssetLocally(Either.Right(Unit))
            }

        useCase(message.conversationId, message.id).toEither().shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.deleteAssetLocally(ASSET_CONTENT_REMOTE_DATA.assetId, ASSET_CONTENT_REMOTE_DATA.assetDomain)
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.assetRepository.deleteAssetLocally("preview-id", ASSET_CONTENT_REMOTE_DATA.assetDomain) }
    }

    @Test
    fun givenAssetDeletionFails_whenExpiring_thenStillMarkAndDeleteMessage() = runTest {
        val message = MESSAGE_REGULAR.copy(content = MessageContent.Asset(ASSET_IMAGE_CONTENT))
        val failure = StorageFailure.Generic(IllegalStateException("file deletion failed"))
        val (arrangement, useCase) = Arrangement().arrange {
            withGetMessageById(Either.Right(message))
            withDeleteAssetLocally(Either.Left(failure))
            withMarkAsDeleted(Either.Right(Unit))
            withCurrentClientIdSuccess(CURRENT_CLIENT_ID)
            withSelfConversationIds(SELF_CONVERSATION_ID)
            withSendMessageSucceed()
            withDeleteMessage(Either.Right(Unit))
        }

        useCase(message.conversationId, message.id).toEither().shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.deleteAssetLocally(ASSET_CONTENT_REMOTE_DATA.assetId, ASSET_CONTENT_REMOTE_DATA.assetDomain)
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageRepository.markMessageAsDeleted(message.id, message.conversationId) }
        verifySuspend(VerifyMode.exactly(2)) { arrangement.messageSender.sendMessage(any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageRepository.deleteMessage(message.id, message.conversationId) }
    }

    @Test
    fun givenMarkingMessageDeletedFails_whenExpiring_thenReturnRequiredFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, useCase) = Arrangement().arrange {
            withGetMessageById(Either.Right(MESSAGE_REGULAR))
            withMarkAsDeleted(Either.Left(failure))
        }

        assertEquals(Either.Left(failure), useCase(MESSAGE_REGULAR.conversationId, MESSAGE_REGULAR.id).toEither())
        verifySuspend(VerifyMode.not) { arrangement.messageSender.sendMessage(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.messageRepository.deleteMessage(any(), any()) }
    }

    @Test
    fun givenPermanentDeletionFails_whenExpiring_thenReturnRequiredFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement().arrange {
            withGetMessageById(Either.Right(MESSAGE_REGULAR))
            withMarkAsDeleted(Either.Right(Unit))
            withCurrentClientIdSuccess(CURRENT_CLIENT_ID)
            withSelfConversationIds(SELF_CONVERSATION_ID)
            withSendMessageSucceed()
            withDeleteMessage(Either.Left(failure))
        }

        assertEquals(Either.Left(failure), useCase(MESSAGE_REGULAR.conversationId, MESSAGE_REGULAR.id).toEither())
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

    private class Arrangement {
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val messageRepository = mock<MessageRepository>()
        val messageSender = mock<MessageSender>()
        val selfConversationIdProvider = mock<SelfConversationIdProvider>()
        val assetRepository = mock<AssetRepository>()
        val syncManager = mock<SyncManager>()

        private val useCase: DeleteEphemeralMessageForSelfUserAsReceiverUseCase =
            DeleteEphemeralMessageForSelfUserAsReceiverUseCaseImpl(
                messageRepository = messageRepository,
                messageSender = messageSender,
                selfUserId = SELF_USER_ID,
                selfConversationIdProvider = selfConversationIdProvider,
                assetRepository = assetRepository,
                currentClientIdProvider = currentClientIdProvider,
                syncManager = syncManager,
            )

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, DeleteEphemeralMessageForSelfUserAsReceiverUseCase> {
            withDeleteAssetLocally(Either.Right(Unit))
            block()
            return this to useCase
        }

        suspend fun withMarkAsDeleted(result: Either<com.wire.kalium.common.error.StorageFailure, Unit>) = apply {
            everySuspend { messageRepository.markMessageAsDeleted(any(), any()) } returns result
        }

        suspend fun withCurrentClientIdSuccess(currentClientId: ClientId) = apply {
            everySuspend { currentClientIdProvider.invoke() } returns Either.Right(currentClientId)
        }

        suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) = apply {
            everySuspend { selfConversationIdProvider.invoke() } returns Either.Right(conversationIds)
        }

        suspend fun withGetMessageById(result: Either<com.wire.kalium.common.error.StorageFailure, Message>) = apply {
            everySuspend { messageRepository.getMessageById(any(), any()) } returns result
        }

        suspend fun withSendMessageSucceed() = apply {
            everySuspend { messageSender.sendMessage(any(), any()) } returns Either.Right(Unit)
        }

        suspend fun withDeleteMessage(result: Either<com.wire.kalium.common.error.CoreFailure, Unit>) = apply {
            everySuspend { messageRepository.deleteMessage(any(), any()) } returns result
        }

        suspend fun withDeleteAssetLocally(result: Either<com.wire.kalium.common.error.CoreFailure, Unit>) = apply {
            everySuspend { assetRepository.deleteAssetLocally(any(), any()) } returns result
        }

        init {
            everySuspend { syncManager.waitUntilLive() } returns Unit
        }
    }
}
