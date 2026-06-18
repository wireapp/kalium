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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageThreadRoot
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageCompositeEditHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageMultipartEditHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandler
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandler
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.io.encoding.Base64
import kotlin.test.Test

class ApplicationMessageHandlerTest {

    @Test
    fun givenValidNewImageMessageEvent_whenHandling_shouldCallTheAssetMessageHandler() = runTest {
        val messageId = "messageId"
        val validImageContent = MessageContent.Asset(
            AssetContent(
                1000, "some-image.jpg", "image/jpg", AssetContent.AssetMetadata.Image(200, 200),
                AssetContent.RemoteData(
                    ByteArray(16), ByteArray(16), "assetid", null, null, null
                )
            )
        )
        val protoContent = ProtoContent.Readable(
            messageId,
            validImageContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )
        val coreFailure = StorageFailure.DataNotFound
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFileSharingEnabled()
            .withErrorGetMessageById(coreFailure)
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetMessageHandler.handle(
                matches {
                    it.content is MessageContent.Asset
                }
            )
        }
    }

    @Test
    fun givenButtonActionMessage_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        val messageId = "messageId"
        val validImageContent = MessageContent.ButtonAction(
            referencedMessageId = messageId,
            buttonId = "buttonId"
        )
        val protoContent = ProtoContent.Readable(
            messageId,
            validImageContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.buttonActionHandler.handle(any(), any(), any(), any())
        }
    }

    @Test
    fun givenMessageCompositeEdited_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        val messageId = "messageId"
        val validCompositeEditedContent = MessageContent.CompositeEdited(
            editMessageId = messageId,
            newTextContent = MessageContent.Text(
                value = "Edited text",
                mentions = emptyList()
            ),
            newButtonList = emptyList()
        )
        val protoContent = ProtoContent.Readable(
            messageId,
            validCompositeEditedContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withMessageCompositeEditHandler()
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageCompositeEditHandler.handle(any(), any())
        }
    }

    @Test
    fun givenMessageMultipartEdited_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        val messageId = "messageId"
        val validMultipartEditedContent = MessageContent.MultipartEdited(
            editMessageId = messageId,
            newTextContent = "Edited text",
        )
        val protoContent = ProtoContent.Readable(
            messageId,
            validMultipartEditedContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withMessageMultipartEditHandler()
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageMultipartEditHandler.handle(any(), any())
        }
    }

    @Test
    fun givenButtonActionConfirmationMessage_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        val messageId = "messageId"
        val validImageContent = MessageContent.ButtonActionConfirmation(
            referencedMessageId = messageId,
            buttonId = "buttonId"
        )
        val protoContent = ProtoContent.Readable(
            messageId,
            validImageContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withButtonActionConfirmation(Either.Right(Unit))
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.buttonActionConfirmationHandler.handle(any(), any(), any())
        }
    }

    @Test
    fun givenDataTransferEventReceived_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        // given
        val messageId = "messageId"
        val dataTransferContent = MessageContent.DataTransfer(
            trackingIdentifier = MessageContent.DataTransfer.TrackingIdentifier(
                identifier = "abcd-1234-efgh-5678"
            )
        )
        val protoContent = ProtoContent.Readable(
            messageId,
            dataTransferContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)

        // when
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.dataTransferEventHandler.handle(any(), any())
        }
    }

    @Test
    fun givenThreadFollowStatusFromSelf_whenHandling_thenThreadFollowStateIsUpdated() = runTest {
        val messageId = "messageId"
        val threadId = "thread-id"
        val threadFollowContent = MessageContent.ThreadFollow(
            conversationId = TestConversation.ID,
            threadId = threadId,
            isFollowing = false,
        )
        val protoContent = ProtoContent.Readable(
            messageUid = messageId,
            messageContent = threadFollowContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .withThreadFollowStateUpdateReturning(Either.Right(Unit))
            .arrange()
        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent, senderUserId = TestUser.SELF.id)

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageThreadRepository.updateThreadFollowState(
                TestConversation.ID,
                threadId,
                false,
            )
        }
    }

    @Test
    fun givenThreadFollowStatusFromOtherUser_whenHandling_thenThreadFollowStateIsIgnored() = runTest {
        val messageId = "messageId"
        val threadId = "thread-id"
        val threadFollowContent = MessageContent.ThreadFollow(
            conversationId = TestConversation.ID,
            threadId = threadId,
            isFollowing = false,
        )
        val protoContent = ProtoContent.Readable(
            messageUid = messageId,
            messageContent = threadFollowContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .withThreadFollowStateUpdateReturning(Either.Right(Unit))
            .arrange()
        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent, senderUserId = TestUser.USER_ID)

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.not) {
            arrangement.messageThreadRepository.updateThreadFollowState(any(), any(), any())
        }
    }

    @Test
    fun givenInCallReactionReceived_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        // given
        val messageId = "messageId"
        val inCallReactionContent = MessageContent.InCallEmoji(
            emojis = mapOf("1" to 1)
        )
        val protoContent = ProtoContent.Readable(
            messageId,
            inCallReactionContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val (arrangement, messageHandler) = Arrangement()
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)

        // when
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.inCallReactionsRepository.addInCallReaction(messageEvent.conversationId, messageEvent.senderUserId, setOf("1"))
        }
    }

    @Test
    fun givenCallingMessageReceived_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        // given
        val messageId = "messageId"
        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)
        val callingContent = MessageContent.Calling(value = "json content", conversationId = messageEvent.conversationId)
        val protoContent = ProtoContent.Readable(
            messageUid = messageId,
            messageContent = callingContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .arrange()

        // when
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.callingMessageHandler.handle(any(), callingContent)
        }
    }

    @Test
    fun givenIncomingThreadedMessageWithoutRootMapping_whenHandling_thenInferAndUpsertRootMapping() = runTest {
        val messageId = "thread-reply-message-id"
        val threadId = "thread-root-message-id"
        val textContent = MessageContent.Text(
            value = "Reply in thread",
            mentions = emptyList(),
        )
        val protoContent = ProtoContent.Readable(
            messageUid = messageId,
            messageContent = textContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
            threadId = threadId,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withMessageThreadUpsertDefaults()
            .withThreadRootLookupReturning(Either.Right(null))
            .withErrorGetMessageById(StorageFailure.DataNotFound)
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageThreadRepository.upsertThreadItem(
                messageEvent.conversationId,
                messageId,
                threadId,
                false,
                messageEvent.messageInstant,
                Message.Visibility.VISIBLE,
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageThreadRepository.upsertThreadRoot(
                messageEvent.conversationId,
                threadId,
                threadId,
                messageEvent.messageInstant
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageThreadRepository.upsertThreadItem(
                messageEvent.conversationId,
                threadId,
                threadId,
                true,
                messageEvent.messageInstant,
                Message.Visibility.VISIBLE,
            )
        }
    }

    @Test
    fun givenIncomingThreadedMessageWithExistingRootMapping_whenHandling_thenDoNotDuplicateRootMapping() = runTest {
        val messageId = "thread-reply-message-id"
        val threadId = "thread-root-message-id"
        val textContent = MessageContent.Text(
            value = "Reply in thread",
            mentions = emptyList(),
        )
        val protoContent = ProtoContent.Readable(
            messageUid = messageId,
            messageContent = textContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
            threadId = threadId,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withMessageThreadUpsertDefaults()
            .withThreadRootLookupReturning(
                Either.Right(
                    MessageThreadRoot(
                        conversationId = TestConversation.ID,
                        rootMessageId = threadId,
                        threadId = threadId,
                        createdAt = Clock.System.now(),
                    )
                )
            )
            .arrange()

        val encodedEncryptedContent = Base64.encode("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent)

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageThreadRepository.upsertThreadItem(
                messageEvent.conversationId,
                messageId,
                threadId,
                false,
                messageEvent.messageInstant,
                Message.Visibility.VISIBLE,
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageThreadRepository.upsertThreadRoot(
                any(),
                any(),
                any(),
                any(),
            )
        }
        verifySuspend(VerifyMode.not) {
            arrangement.messageThreadRepository.upsertThreadItem(
                any(),
                threadId,
                any(),
                true,
                any(),
                any(),
            )
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val persistMessage = mock<PersistMessageUseCase>(MockMode.autoUnit)
        val messageRepository = mock<MessageRepository>(MockMode.autoUnit)
        private val userRepository = mock<UserRepository>(MockMode.autoUnit)
        val userConfigRepository = mock<UserConfigRepository>(MockMode.autoUnit)
        val persistReactionsUseCase = mock<PersistReactionUseCase>(MockMode.autoUnit)
        val messageTextEditHandler = mock<MessageTextEditHandler>(MockMode.autoUnit)
        val messageMultipartEditHandler = mock<MessageMultipartEditHandler>(MockMode.autoUnit)
        val lastReadContentHandler = mock<LastReadContentHandler>(MockMode.autoUnit)
        val clearConversationContentHandler = mock<ClearConversationContentHandler>(MockMode.autoUnit)
        val deleteForMeHandler = mock<DeleteForMeHandler>(MockMode.autoUnit)
        val deleteMessageHandler = mock<DeleteMessageHandler>(MockMode.autoUnit)
        val receiptMessageHandler = mock<ReceiptMessageHandler>(MockMode.autoUnit)
        val assetMessageHandler = mock<AssetMessageHandler>(MockMode.autoUnit)
        val buttonActionConfirmationHandler = mock<ButtonActionConfirmationHandler>(MockMode.autoUnit)
        val inCallReactionsRepository = mock<InCallReactionsRepository>(MockMode.autoUnit)
        val dataTransferEventHandler = mock<DataTransferEventHandler>(MockMode.autoUnit)
        val buttonActionHandler = mock<ButtonActionHandler>(MockMode.autoUnit)
        val messageCompositeEditHandler = mock<MessageCompositeEditHandler>(MockMode.autoUnit)
        val callingMessageHandler = mock<CallingMessageHandler>(MockMode.autoUnit)
        val messageThreadRepository = mock<MessageThreadRepository>(MockMode.autoUnit)

        private val applicationMessageHandler = ApplicationMessageHandlerImpl(
            userRepository,
            messageRepository,
            assetMessageHandler,
            persistMessage,
            persistReactionsUseCase,
            messageTextEditHandler,
            messageMultipartEditHandler,
            lastReadContentHandler,
            clearConversationContentHandler,
            deleteForMeHandler,
            deleteMessageHandler,
            MessageContentEncoder(),
            receiptMessageHandler,
            buttonActionConfirmationHandler,
            dataTransferEventHandler,
            inCallReactionsRepository,
            buttonActionHandler,
            messageCompositeEditHandler,
            messageThreadRepository,
            callingMessageHandler,
            TestUser.SELF.id
        )

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                persistMessage.invoke(any())
            }.returns(result)
        }

        fun withFileSharingEnabled() = apply {
            everySuspend {
                userConfigRepository.isFileSharingEnabled()
            }.returns(
                Either.Right(
                    FileSharingStatus(
                        state = FileSharingStatus.Value.EnabledAll,
                        isStatusChanged = false
                    )
                )
            )
        }

        fun withErrorGetMessageById(storageFailure: StorageFailure) = apply {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            }.returns(Either.Left(storageFailure))
        }

        fun withButtonActionConfirmation(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                buttonActionConfirmationHandler.handle(any(), any(), any())
            }.returns(result)
        }

        fun withMessageCompositeEditHandler() = apply {
            everySuspend {
                messageCompositeEditHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withMessageMultipartEditHandler() = apply {
            everySuspend {
                messageMultipartEditHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withButtonAction() = apply {
            everySuspend {
                buttonActionHandler.handle(any(), any(), any(), any())
            }.returns(Unit)
        }

        fun withThreadRootLookupReturning(result: Either<StorageFailure, MessageThreadRoot?>) = apply {
            everySuspend {
                messageThreadRepository.getThreadByRootMessage(any(), any())
            }.returns(result)
        }

        fun withMessageThreadUpsertDefaults() = apply {
            everySuspend {
                messageThreadRepository.upsertThreadRoot(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
            everySuspend {
                messageThreadRepository.upsertThreadItem(any(), any(), any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withThreadFollowStateUpdateReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                messageThreadRepository.updateThreadFollowState(any(), any(), any())
            }.returns(result)
        }

        fun arrange() = this to applicationMessageHandler
    }
}
