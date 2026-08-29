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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.history.HistoryClient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolver
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.AvailabilityMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.handler.ClientActionMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.DataTransferEventHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.handler.DeleteMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.InCallEmojiMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageCompositeEditHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageMultipartEditHandler
import com.wire.kalium.logic.sync.receiver.handler.MessageTextEditHandler
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandler
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

private val SELF_USER_ID = UserId("41d2b365-f4a9-4ba1-bddf-5afb8aca6786", "domain")
private val TEST_CONVERSATION_ID = ConversationId("valueConvo", "domainConvo")
private val TEST_SENDER_CLIENT_ID = ClientId("test")
private val TEST_MESSAGE_INSTANT = Instant.UNIX_FIRST_DATE

private data class NewMessageFixture(
    val conversationId: ConversationId,
    val senderUserId: UserId,
    val senderClientId: ClientId,
    val messageInstant: Instant,
)

private fun newMessageEvent() = NewMessageFixture(
    conversationId = TEST_CONVERSATION_ID,
    senderUserId = SELF_USER_ID,
    senderClientId = TEST_SENDER_CLIENT_ID,
    messageInstant = TEST_MESSAGE_INSTANT,
)

@Suppress("TooManyFunctions")
class ApplicationMessageHandlerTest {

    @Test
    fun givenAvailabilityMessage_whenHandling_thenAvailabilityLeafReceivesSignalingEnvelopeExactlyOnce() = runTest {
        val messageEvent = newMessageEvent()
        val availabilityContent = MessageContent.Availability(UserAvailabilityStatus.BUSY)
        val protoContent = ProtoContent.Readable(
            messageUid = "availability-signaling-id",
            messageContent = availabilityContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val expectedSignaling = Message.Signaling(
            id = protoContent.messageUid,
            content = availabilityContent,
            conversationId = messageEvent.conversationId,
            date = messageEvent.messageInstant,
            senderUserId = messageEvent.senderUserId,
            senderClientId = messageEvent.senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = messageEvent.senderUserId == SELF_USER_ID,
            expirationData = null,
        )
        val (arrangement, messageHandler) = Arrangement().arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.availabilityMessageHandler.handle(
                matches<Message.Signaling> { it == expectedSignaling },
                matches { it == availabilityContent },
            )
        }
    }

    @Test
    fun givenClientActionMessage_whenHandling_thenClientActionLeafReceivesExactSignalingEnvelopeOnce() = runTest {
        val messageEvent = newMessageEvent()
        val protoContent = ProtoContent.Readable(
            messageUid = "client-action-signaling-id",
            messageContent = MessageContent.ClientAction,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
            expiresAfterMillis = 30_000L,
        )
        val expectedSignaling = Message.Signaling(
            id = protoContent.messageUid,
            content = MessageContent.ClientAction,
            conversationId = messageEvent.conversationId,
            date = messageEvent.messageInstant,
            senderUserId = messageEvent.senderUserId,
            senderClientId = messageEvent.senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = messageEvent.senderUserId == SELF_USER_ID,
            expirationData = Message.ExpirationData(30.seconds),
        )
        val (arrangement, messageHandler) = Arrangement().arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientActionMessageHandler.handle(
                matches<Message.Signaling> { it == expectedSignaling },
            )
        }
    }

    @Test
    fun givenDeleteForMeMessage_whenHandling_thenExistingDeleteForMeLeafIsInvoked() = runTest {
        val messageEvent = newMessageEvent()
        val deleteForMe = MessageContent.DeleteForMe(
            messageId = "deleted-message-id",
            conversationId = messageEvent.conversationId,
        )
        val protoContent = ProtoContent.Readable(
            messageUid = "signaling-message-id",
            messageContent = deleteForMe,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val (arrangement, messageHandler) = Arrangement().arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deleteForMeHandler.handle(
                matches { it.conversationId == messageEvent.conversationId },
                matches { it == deleteForMe },
            )
        }
    }

    @Test
    fun givenClearedMessage_whenHandling_thenExistingClearLeafReceivesExactTransactionEnvelopeAndPayloadOnce() = runTest {
        val messageEvent = newMessageEvent()
        val payloadConversationId = messageEvent.conversationId.copy(value = "payload-conversation-id")
        val cleared = MessageContent.Cleared(
            conversationId = payloadConversationId,
            time = messageEvent.messageInstant,
            needToRemoveLocally = true,
        )
        val protoContent = ProtoContent.Readable(
            messageUid = "cleared-signaling-message-id",
            messageContent = cleared,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val expectedSignaling = Message.Signaling(
            id = protoContent.messageUid,
            content = cleared,
            conversationId = messageEvent.conversationId,
            date = messageEvent.messageInstant,
            senderUserId = messageEvent.senderUserId,
            senderClientId = messageEvent.senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = messageEvent.senderUserId == SELF_USER_ID,
            expirationData = null,
        )
        val (arrangement, messageHandler) = Arrangement().arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearConversationContentHandler.handle(
                matches { it === arrangement.transactionContext },
                matches { it == expectedSignaling },
                matches { it == cleared },
            )
        }
    }

    @Test
    fun givenDeleteMessage_whenHandling_thenExistingDeleteLeafReceivesExactPayloadAndEnvelopeIds() = runTest {
        val messageEvent = newMessageEvent()
        val deleteMessage = MessageContent.DeleteMessage("deleted-message-id")
        val protoContent = ProtoContent.Readable(
            messageUid = "delete-signaling-message-id",
            messageContent = deleteMessage,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val (arrangement, messageHandler) = Arrangement().arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deleteMessageHandler(
                matches { it == deleteMessage },
                matches { it == messageEvent.conversationId },
                matches { it == messageEvent.senderUserId },
            )
        }
    }

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
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        val messageEvent = newMessageEvent()
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

        val messageEvent = newMessageEvent()
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

        val messageEvent = newMessageEvent()
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
    fun givenMessageTextEdited_whenHandling_thenExactHandlerArgumentsAreForwarded() = runTest {
        val textEditedContent = MessageContent.TextEdited(
            editMessageId = "original-message-id",
            newContent = "Edited text",
        )
        val protoContent = ProtoContent.Readable(
            messageUid = "text-edit-signaling-id",
            messageContent = textEditedContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val messageEvent = newMessageEvent()
        val expectedSignaling = Message.Signaling(
            id = protoContent.messageUid,
            content = textEditedContent,
            conversationId = messageEvent.conversationId,
            date = messageEvent.messageInstant,
            senderUserId = messageEvent.senderUserId,
            senderClientId = messageEvent.senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = messageEvent.senderUserId == SELF_USER_ID,
            expirationData = null,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withMessageTextEditHandler()
            .arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageTextEditHandler.handle(
                matches<Message.Signaling> { it == expectedSignaling },
                matches { it == textEditedContent },
            )
        }
    }

    @Test
    fun givenMessageMultipartEdited_whenHandling_thenExactHandlerArgumentsAreForwarded() = runTest {
        val multipartEditedContent = MessageContent.MultipartEdited(
            editMessageId = "original-message-id",
            newTextContent = "Edited text",
        )
        val protoContent = ProtoContent.Readable(
            messageUid = "multipart-edit-signaling-id",
            messageContent = multipartEditedContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val messageEvent = newMessageEvent()
        val expectedSignaling = Message.Signaling(
            id = protoContent.messageUid,
            content = multipartEditedContent,
            conversationId = messageEvent.conversationId,
            date = messageEvent.messageInstant,
            senderUserId = messageEvent.senderUserId,
            senderClientId = messageEvent.senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = messageEvent.senderUserId == SELF_USER_ID,
            expirationData = null,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withMessageMultipartEditHandler()
            .arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageMultipartEditHandler.handle(
                matches<Message.Signaling> { it == expectedSignaling },
                matches { it == multipartEditedContent },
            )
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

        val messageEvent = newMessageEvent()
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

        val messageEvent = newMessageEvent()

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
    fun givenInCallEmojiMessage_whenHandling_thenInCallEmojiLeafReceivesExactEnvelopeAndContentOnce() = runTest {
        val inCallReactionContent = MessageContent.InCallEmoji(
            emojis = linkedMapOf("1" to 1, "2" to 2),
        )
        val protoContent = ProtoContent.Readable(
            messageUid = "in-call-emoji-signaling-id",
            messageContent = inCallReactionContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val messageEvent = newMessageEvent()
        val expectedSignaling = Message.Signaling(
            id = protoContent.messageUid,
            content = inCallReactionContent,
            conversationId = messageEvent.conversationId,
            date = messageEvent.messageInstant,
            senderUserId = messageEvent.senderUserId,
            senderClientId = messageEvent.senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = messageEvent.senderUserId == SELF_USER_ID,
            expirationData = null,
        )
        val (arrangement, messageHandler) = Arrangement().arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.inCallEmojiMessageHandler.handle(
                matches<Message.Signaling> { it == expectedSignaling },
                matches { it === inCallReactionContent },
            )
        }
    }

    @Test
    fun givenCallingMessageReceived_whenHandling_thenCorrectHandlerIsInvoked() = runTest {
        // given
        val messageId = "messageId"
        val messageEvent = newMessageEvent()
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
    fun givenQuotedTextMessage_whenHandling_thenQuoteIsVerifiedBeforeAdjustedMessageIsPersisted() = runTest {
        val messageEvent = newMessageEvent()
        val quoteReference = MessageContent.QuoteReference("quoted-message", byteArrayOf(1, 2), false)
        val adjustedQuoteReference = quoteReference.copy(isVerified = true)
        val protoContent = ProtoContent.Readable(
            messageUid = "text-message",
            messageContent = MessageContent.Text("quoted text", quotedMessageReference = quoteReference),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withQuoteVerification(adjustedQuoteReference)
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.order) {
            arrangement.incomingQuotedMessageVerifier.invoke(
                matches { it == messageEvent.conversationId },
                matches { it === quoteReference },
            )
            arrangement.persistMessage.invoke(
                matches<Message.Regular> {
                    (it.content as? MessageContent.Text)?.quotedMessageReference === adjustedQuoteReference
                }
            )
        }
    }

    @Test
    fun givenQuotedMultipartMessage_whenHandling_thenQuoteIsVerifiedBeforeAdjustedMessageIsPersisted() = runTest {
        val messageEvent = newMessageEvent()
        val quoteReference = MessageContent.QuoteReference("quoted-message", byteArrayOf(1, 2), false)
        val adjustedQuoteReference = quoteReference.copy(isVerified = true)
        val protoContent = ProtoContent.Readable(
            messageUid = "multipart-message",
            messageContent = MessageContent.Multipart("quoted multipart", quotedMessageReference = quoteReference),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withQuoteVerification(adjustedQuoteReference)
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.order) {
            arrangement.incomingQuotedMessageVerifier.invoke(
                matches { it == messageEvent.conversationId },
                matches { it === quoteReference },
            )
            arrangement.persistMessage.invoke(
                matches<Message.Regular> {
                    (it.content as? MessageContent.Multipart)?.quotedMessageReference === adjustedQuoteReference
                }
            )
        }
    }

    @Test
    fun givenTextMessageWithoutQuote_whenHandling_thenVerificationIsSkippedAndNullReferenceIsPersisted() = runTest {
        val messageEvent = newMessageEvent()
        val protoContent = ProtoContent.Readable(
            messageUid = "text-message",
            messageContent = MessageContent.Text("plain text"),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.not) {
            arrangement.incomingQuotedMessageVerifier.invoke(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches<Message.Regular> {
                    (it.content as? MessageContent.Text)?.quotedMessageReference == null
                }
            )
        }
    }

    @Test
    fun givenMultipartMessageWithoutQuote_whenHandling_thenVerificationIsSkippedAndNullReferenceIsPersisted() = runTest {
        val messageEvent = newMessageEvent()
        val protoContent = ProtoContent.Readable(
            messageUid = "multipart-message",
            messageContent = MessageContent.Multipart("plain multipart"),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        verifySuspend(VerifyMode.not) {
            arrangement.incomingQuotedMessageVerifier.invoke(any(), any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches<Message.Regular> {
                    (it.content as? MessageContent.Multipart)?.quotedMessageReference == null
                }
            )
        }
    }

    @Test
    fun givenTextMessageWithLinkPreviewImage_whenHandling_thenLinkPreviewResolutionIsTriggered() = runTest {
        val messageId = "messageId"
        val textContent = MessageContent.Text(
            value = "hello https://example.com",
            linkPreviews = listOf(
                com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview(
                    url = "https://example.com",
                    urlOffset = 6,
                    image = com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset(
                        mimeType = "image/png",
                        assetDataPath = null,
                        assetDataSize = 0,
                        assetHeight = 100,
                        assetWidth = 100,
                        assetKey = "asset-key",
                        assetToken = "asset-token",
                        assetDomain = "wire.com",
                        otrKey = byteArrayOf(1),
                        sha256Key = byteArrayOf(2)
                    )
                )
            )
        )
        val protoContent = ProtoContent.Readable(
            messageUid = messageId,
            messageContent = textContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )
        val (arrangement, messageHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        val messageEvent = newMessageEvent()
        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.linkPreviewImagesResolver.invoke(messageEvent.conversationId, messageId)
        }
    }

    @Test
    fun givenClientActionMessage_whenHandling_thenCryptoSessionResetMessageIsPersisted() = runTest {
        val (arrangement, handler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        val event = dispatch(arrangement, handler, MessageContent.ClientAction)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches {
                    it is Message.System &&
                            it.content == MessageContent.CryptoSessionReset &&
                            it.conversationId == event.conversationId &&
                            it.senderUserId == event.senderUserId
                }
            )
        }
    }

    @Test
    fun givenReactionMessage_whenHandling_thenReactionUseCaseIsInvoked() = runTest {
        val content = MessageContent.Reaction(messageId = "reacted-message", emojiSet = setOf("👍"))
        val (arrangement, handler) = Arrangement()
            .withPersistReaction()
            .arrange()

        val event = dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistReactionsUseCase.invoke(content, event.conversationId, event.senderUserId, event.messageInstant)
        }
    }

    @Test
    fun givenDeleteMessageSignal_whenHandling_thenDeleteMessageHandlerIsInvoked() = runTest {
        val content = MessageContent.DeleteMessage("deleted-message")
        val (arrangement, handler) = Arrangement().arrange()

        val event = dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deleteMessageHandler.invoke(content, event.conversationId, event.senderUserId)
        }
    }

    @Test
    fun givenDeleteForMeSignal_whenHandling_thenDeleteForMeHandlerIsInvoked() = runTest {
        val content = MessageContent.DeleteForMe("deleted-message", TestEvent.newMessageEvent("content").conversationId)
        val (arrangement, handler) = Arrangement().arrange()

        dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deleteForMeHandler.handle(any(), content)
        }
    }

    @Test
    fun givenTextEditSignal_whenHandling_thenTextEditHandlerIsInvoked() = runTest {
        val content = MessageContent.TextEdited(editMessageId = "edited-message", newContent = "new content")
        val (arrangement, handler) = Arrangement()
            .withMessageTextEditHandler()
            .arrange()

        dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageTextEditHandler.handle(any(), content)
        }
    }

    @Test
    fun givenLastReadSignal_whenHandling_thenLastReadHandlerIsInvoked() = runTest {
        val event = TestEvent.newMessageEvent("content")
        val content = MessageContent.LastRead("last-read-message", event.conversationId, event.messageInstant)
        val (arrangement, handler) = Arrangement().arrange()

        dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.lastReadContentHandler.handle(any(), content)
        }
    }

    @Test
    fun givenClearedSignal_whenHandling_thenClearConversationHandlerIsInvoked() = runTest {
        val event = TestEvent.newMessageEvent("content")
        val content = MessageContent.Cleared(event.conversationId, event.messageInstant, needToRemoveLocally = true)
        val (arrangement, handler) = Arrangement().arrange()

        dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearConversationContentHandler.handle(any(), any(), content)
        }
    }

    @Test
    fun givenReceiptSignal_whenHandling_thenReceiptHandlerIsInvoked() = runTest {
        val content = MessageContent.Receipt(ReceiptType.READ, listOf("read-message"))
        val (arrangement, handler) = Arrangement().arrange()

        dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.receiptMessageHandler.handle(any(), content)
        }
    }

    @Test
    fun givenHiddenUnknownMessage_whenHandling_thenHiddenMessageIsPersisted() = runTest {
        val content = MessageContent.Unknown(typeName = "future-message", hidden = true)
        val (arrangement, handler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches { it is Message.Regular && it.content == content && it.visibility == Message.Visibility.HIDDEN }
            )
        }
    }

    @Test
    fun givenSelfKnockWithExpiration_whenHandling_thenMessageMetadataIsPersisted() = runTest {
        val content = MessageContent.Knock(hotKnock = true)
        val (arrangement, handler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        dispatch(
            arrangement = arrangement,
            handler = handler,
            content = content,
            senderUserId = TestUser.SELF.id,
            expiresAfterMillis = 2.seconds.inWholeMilliseconds,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches {
                    it is Message.Regular && it.isSelfMessage &&
                            it.expirationData?.expireAfter == 2.seconds && it.content == content
                }
            )
        }
    }

    @Test
    fun givenRegularPersistableMessageTypes_whenHandling_thenEveryMessageIsPersisted() = runTest {
        val contents = listOf(
            MessageContent.Composite(textContent = null, buttonList = emptyList()),
            MessageContent.Location(latitude = 1F, longitude = 2F),
            MessageContent.FailedDecryption(
                isDecryptionResolved = false,
                senderUserId = TestUser.USER_ID,
            ),
            MessageContent.Multipart(value = "multipart"),
        )
        val (arrangement, handler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        contents.forEach { dispatch(arrangement, handler, it) }

        verifySuspend(VerifyMode.exactly(contents.size)) {
            arrangement.persistMessage.invoke(any())
        }
    }

    @Test
    fun givenRestrictedAssetMessage_whenHandling_thenMessageIsIgnored() = runTest {
        val content = MessageContent.RestrictedAsset(mimeType = "image/png", sizeInBytes = 10, name = "restricted.png")
        val (arrangement, handler) = Arrangement().arrange()

        dispatch(arrangement, handler, content)

        verifySuspend(VerifyMode.not) { arrangement.persistMessage.invoke(any()) }
        verifySuspend(VerifyMode.not) { arrangement.assetMessageHandler.handle(any()) }
    }

    @Test
    fun givenPendingApplicationSideEffects_whenFlushing_thenLastReadsAreFlushed() = runTest {
        val (arrangement, handler) = Arrangement().arrange()

        handler.flushPendingSideEffects()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.lastReadContentHandler.flushPendingLastReads()
        }
    }

    @Test
    fun givenDecryptionErrorFromSelf_whenHandling_thenVisibleSelfMessageIsPersisted() = runTest {
        val event = TestEvent.newMessageEvent(Base64.encode("Hello".encodeToByteArray()))
        val content = MessageContent.FailedDecryption(
            isDecryptionResolved = false,
            senderUserId = TestUser.SELF.id,
        )
        val (arrangement, handler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        handler.handleDecryptionError(
            eventId = event.id,
            conversationId = event.conversationId,
            messageInstant = event.messageInstant,
            senderUserId = TestUser.SELF.id,
            senderClientId = event.senderClientId,
            content = content,
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches {
                    it is Message.Regular &&
                            it.content == content &&
                            it.isSelfMessage &&
                            it.visibility == Message.Visibility.VISIBLE
                }
            )
        }
    }

    @Test
    fun givenHistoryClientsRequest_whenHandling_thenMessageIsSafelySkipped() = runTest {
        assertHistoryMessageIsSafelySkipped(
            content = MessageContent.History.ClientsRequest,
            expectedMessageType = "History.ClientsRequest",
        )
    }

    @Test
    fun givenHistoryClientsResponse_whenHandling_thenMessageIsSafelySkipped() = runTest {
        assertHistoryMessageIsSafelySkipped(
            content = MessageContent.History.ClientsResponse(listOf(HISTORY_CLIENT)),
            expectedMessageType = "History.ClientsResponse",
        )
    }

    @Test
    fun givenHistoryNewClientAvailable_whenHandling_thenMessageIsSafelySkipped() = runTest {
        assertHistoryMessageIsSafelySkipped(
            content = MessageContent.History.NewClientAvailable(HISTORY_CLIENT),
            expectedMessageType = "History.NewClientAvailable",
        )
    }

    private suspend fun assertHistoryMessageIsSafelySkipped(
        content: MessageContent.History,
        expectedMessageType: String,
    ) {
        val logWriter = RecordingLogWriter()
        val (arrangement, messageHandler) = Arrangement(recordingLogger(logWriter)).arrange()
        val messageEvent = newMessageEvent()
        val protoContent = ProtoContent.Readable(
            messageUid = "messageId",
            messageContent = content,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )

        messageHandler.handleContent(
            arrangement.transactionContext,
            messageEvent.conversationId,
            messageEvent.messageInstant,
            messageEvent.senderUserId,
            messageEvent.senderClientId,
            protoContent,
        )

        val logEntry = logWriter.entries.single()
        assertEquals(Severity.Warn, logEntry.severity)
        assertContains(logEntry.message, "\"outcome\":\"skipped\"")
        assertContains(logEntry.message, "\"reason\":\"unsupported\"")
        assertContains(logEntry.message, "\"messageType\":\"$expectedMessageType\"")
        assertFalse(logEntry.message.contains(HISTORY_CLIENT.id))
        assertFalse(logEntry.message.contains(HISTORY_CLIENT_SECRET_MARKER))
    }

    private suspend fun dispatch(
        arrangement: Arrangement,
        handler: ApplicationMessageHandler,
        content: MessageContent.FromProto,
        senderUserId: UserId = TestUser.USER_ID,
        expiresAfterMillis: Long? = null,
    ) = TestEvent.newMessageEvent(Base64.encode("Hello".encodeToByteArray())).also { event ->
        handler.handleContent(
            arrangement.transactionContext,
            event.conversationId,
            event.messageInstant,
            senderUserId,
            event.senderClientId,
            ProtoContent.Readable(
                messageUid = "messageId",
                messageContent = content,
                expectsReadConfirmation = false,
                legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
                expiresAfterMillis = expiresAfterMillis,
            )
        )
    }

    private class Arrangement(
        kaliumLogger: KaliumLogger = KaliumLogger.disabled(),
    ) {

        val transactionContext = mock<CryptoTransactionContext>()
        val persistMessage = mock<PersistMessageUseCase>(MockMode.autoUnit)
        val incomingQuotedMessageVerifier = mock<IncomingQuotedMessageVerifier>(MockMode.autoUnit)
        val availabilityMessageHandler = mock<AvailabilityMessageHandler>(MockMode.autoUnit)
        val clientActionMessageHandler = mock<ClientActionMessageHandler>(MockMode.autoUnit)
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
        val inCallEmojiMessageHandler = mock<InCallEmojiMessageHandler>(MockMode.autoUnit)
        val dataTransferEventHandler = mock<DataTransferEventHandler>(MockMode.autoUnit)
        val buttonActionHandler = mock<ButtonActionHandler>(MockMode.autoUnit)
        val messageCompositeEditHandler = mock<MessageCompositeEditHandler>(MockMode.autoUnit)
        val callingMessageHandler = mock<CallingMessageHandler>(MockMode.autoUnit)
        val linkPreviewImagesResolver = mock<LinkPreviewImagesResolver>(MockMode.autoUnit)

        private val applicationMessageHandler = ApplicationMessageHandlerImpl(
            availabilityMessageHandler,
            clientActionMessageHandler,
            incomingQuotedMessageVerifier,
            assetMessageHandler,
            persistMessage,
            persistReactionsUseCase,
            messageTextEditHandler,
            messageMultipartEditHandler,
            lastReadContentHandler,
            clearConversationContentHandler,
            deleteForMeHandler,
            deleteMessageHandler,
            receiptMessageHandler,
            buttonActionConfirmationHandler,
            dataTransferEventHandler,
            inCallEmojiMessageHandler,
            buttonActionHandler,
            messageCompositeEditHandler,
            callingMessageHandler,
            linkPreviewImagesResolver,
            SELF_USER_ID,
            kaliumLogger,
        )

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                persistMessage.invoke(any())
            }.returns(result)
            every {
                linkPreviewImagesResolver.invoke(any(), any())
            } returns Unit
        }

        fun withQuoteVerification(result: MessageContent.QuoteReference) = apply {
            everySuspend {
                incomingQuotedMessageVerifier.invoke(any(), any())
            }.returns(result)
        }

        fun withButtonActionConfirmation(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                buttonActionConfirmationHandler.handle(any(), any(), any())
            }.returns(result)
        }

        fun withPersistReaction() = apply {
            everySuspend {
                persistReactionsUseCase.invoke(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withMessageTextEditHandler() = apply {
            everySuspend {
                messageTextEditHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withMessageCompositeEditHandler() = apply {
            everySuspend {
                messageCompositeEditHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withMessageTextEditHandler() = apply {
            everySuspend {
                messageTextEditHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun withMessageMultipartEditHandler() = apply {
            everySuspend {
                messageMultipartEditHandler.handle(any(), any())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to applicationMessageHandler
    }

    private class RecordingLogWriter : LogWriter() {
        val entries = mutableListOf<LogEntry>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += LogEntry(severity, message)
        }
    }

    private data class LogEntry(
        val severity: Severity,
        val message: String,
    )

    private companion object {
        const val HISTORY_CLIENT_SECRET_MARKER = "history-client-secret"

        val HISTORY_CLIENT = HistoryClient(
            id = "history-client-id",
            creationTime = kotlinx.datetime.Instant.DISTANT_PAST,
            secret = HistoryClient.Secret(HISTORY_CLIENT_SECRET_MARKER.encodeToByteArray()),
        )

        fun recordingLogger(logWriter: LogWriter) = KaliumLogger(
            config = KaliumLogger.Config(
                initialLevel = KaliumLogLevel.DEBUG,
                initialLogWriterList = listOf(logWriter),
            ),
            tag = "ApplicationMessageHandlerTest",
        )
    }
}
