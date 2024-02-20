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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandler
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandler
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.logic.util.MessageContentEncoder
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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
            messageEvent.timestampIso,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verify(arrangement.assetMessageHandler)
            .suspendFunction(arrangement.assetMessageHandler::handle)
            .with(
                matching {
                    it.content is MessageContent.Asset
                }
            )
            .wasInvoked(exactly = once)
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
            messageEvent.timestampIso,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verify(arrangement.buttonActionConfirmationHandler)
            .suspendFunction(arrangement.buttonActionConfirmationHandler::handle)
            .with(any(), any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        @Mock
        private val callManager = mock(classOf<CallManager>())

        @Mock
        val persistReactionsUseCase = mock(classOf<PersistReactionUseCase>())

        @Mock
        val messageTextEditHandler = mock(classOf<MessageTextEditHandler>())

        @Mock
        val lastReadContentHandler = mock(classOf<LastReadContentHandler>())

        @Mock
        val clearConversationContentHandler = mock(classOf<ClearConversationContentHandler>())

        @Mock
        val deleteForMeHandler = mock(classOf<DeleteForMeHandler>())

        @Mock
        val deleteMessageHandler = mock(classOf<DeleteMessageHandler>())

        @Mock
        val receiptMessageHandler = mock(classOf<ReceiptMessageHandler>())

        @Mock
        val assetMessageHandler = mock(classOf<AssetMessageHandler>())

        @Mock
        val buttonActionConfirmationHandler = mock(classOf<ButtonActionConfirmationHandler>())

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
            TestUser.SELF.id
        )

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFileSharingEnabled() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::isFileSharingEnabled)
                .whenInvoked()
                .thenReturn(
                    Either.Right(
                        FileSharingStatus(
                            state = FileSharingStatus.Value.EnabledAll,
                            isStatusChanged = false
                        )
                    )
                )
        }

        fun withErrorGetMessageById(storageFailure: StorageFailure) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(storageFailure))
        }

        fun withButtonActionConfirmation(result: Either<StorageFailure, Unit>) = apply {
            given(buttonActionConfirmationHandler)
                .suspendFunction(buttonActionConfirmationHandler::handle)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun arrange() = this to applicationMessageHandler
    }
}
