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
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandler
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandler
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.util.MessageContentEncoder
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
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

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent.decodeToString())
        messageHandler.handleContent(
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        coVerify {
            arrangement.assetMessageHandler.handle(
                matches {
                    it.content is MessageContent.Asset
                }
            )
        }.wasInvoked(exactly = once)
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

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent.decodeToString())
        messageHandler.handleContent(
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        coVerify {
            arrangement.buttonActionConfirmationHandler.handle(any(), any(), any())
        }.wasInvoked(exactly = once)
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

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent.decodeToString())

        // when
        messageHandler.handleContent(
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        // then
        coVerify {
            arrangement.dataTransferEventHandler.handle(any(), any())
        }.wasInvoked(exactly = once)
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

        val encodedEncryptedContent = Base64.encodeToBase64("Hello".encodeToByteArray())
        val messageEvent = TestEvent.newMessageEvent(encodedEncryptedContent.decodeToString())

        // when
        messageHandler.handleContent(
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        // then
        coVerify {
            arrangement.inCallReactionsRepository.addInCallReaction(messageEvent.conversationId, messageEvent.senderUserId, setOf("1"))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val messageRepository = mock(MessageRepository::class)

        @Mock
        private val userRepository = mock(UserRepository::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        private val callManager = mock(CallManager::class)

        @Mock
        val persistReactionsUseCase = mock(PersistReactionUseCase::class)

        @Mock
        val messageTextEditHandler = mock(MessageTextEditHandler::class)

        @Mock
        val lastReadContentHandler = mock(LastReadContentHandler::class)

        @Mock
        val clearConversationContentHandler = mock(ClearConversationContentHandler::class)

        @Mock
        val deleteForMeHandler = mock(DeleteForMeHandler::class)

        @Mock
        val deleteMessageHandler = mock(DeleteMessageHandler::class)

        @Mock
        val receiptMessageHandler = mock(ReceiptMessageHandler::class)

        @Mock
        val assetMessageHandler = mock(AssetMessageHandler::class)

        @Mock
        val buttonActionConfirmationHandler = mock(ButtonActionConfirmationHandler::class)

        @Mock
        val inCallReactionsRepository = mock(InCallReactionsRepository::class)

        @Mock
        val dataTransferEventHandler = mock(DataTransferEventHandler::class)

        private val applicationMessageHandler = ApplicationMessageHandlerImpl(
            userRepository,
            messageRepository,
            assetMessageHandler,
            lazyOf(callManager),
            persistMessage,
            persistReactionsUseCase,
            messageTextEditHandler,
            lastReadContentHandler,
            clearConversationContentHandler,
            deleteForMeHandler,
            deleteMessageHandler,
            MessageContentEncoder(),
            receiptMessageHandler,
            buttonActionConfirmationHandler,
            dataTransferEventHandler,
            inCallReactionsRepository,
            TestUser.SELF.id
        )

        suspend fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(result)
        }

        fun withFileSharingEnabled() = apply {
            every {
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

        suspend fun withErrorGetMessageById(storageFailure: StorageFailure) = apply {
            coEvery {
                messageRepository.getMessageById(any(), any())
            }.returns(Either.Left(storageFailure))
        }

        suspend fun withButtonActionConfirmation(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                buttonActionConfirmationHandler.handle(any(), any(), any())
            }.returns(result)
        }

        fun arrange() = this to applicationMessageHandler
    }
}
