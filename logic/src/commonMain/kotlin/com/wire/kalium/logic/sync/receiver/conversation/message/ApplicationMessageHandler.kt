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

import com.wire.kalium.logic.data.call.CallHandIdentity
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.decryptionFailureMessage
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.logic.data.message.cryptoSessionResetMessage
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
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
import com.wire.kalium.logic.sync.receiver.handler.PollVoteHandler
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandler
import com.wire.kalium.logic.feature.message.linkpreview.LinkPreviewImagesResolver
import com.wire.kalium.logic.util.MessageContentEncoder
import kotlin.time.Instant

internal interface ApplicationMessageHandler {

    /**
     * Processes decrypted [content]. Content received as a targeted and/or transient MLS message is rejected
     * unless [isAcceptedDelivery] allows it.
     */
    @Suppress("LongParameterList")
    suspend fun handleContent(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        messageInstant: Instant,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable,
        targeted: Boolean = false,
        transient: Boolean = false,
    )

    @Suppress("LongParameterList")
    suspend fun handleDecryptionError(
        eventId: String,
        conversationId: ConversationId,
        messageInstant: Instant,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: MessageContent.FailedDecryption
    )

    suspend fun flushPendingSideEffects()
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ApplicationMessageHandlerImpl(
    private val userRepository: UserRepository,
    private val messageDAO: MessageDAO,
    private val assetMessageHandler: AssetMessageHandler,
    private val persistMessage: PersistMessageUseCase,
    private val persistReaction: PersistReactionUseCase,
    private val editTextHandler: MessageTextEditHandler,
    private val editMultipartHandler: MessageMultipartEditHandler,
    private val lastReadContentHandler: LastReadContentHandler,
    private val clearConversationContentHandler: ClearConversationContentHandler,
    private val deleteForMeHandler: DeleteForMeHandler,
    private val deleteMessageHandler: DeleteMessageHandler,
    private val messageEncoder: MessageContentEncoder,
    private val receiptMessageHandler: ReceiptMessageHandler,
    private val buttonActionConfirmationHandler: ButtonActionConfirmationHandler,
    private val dataTransferEventHandler: DataTransferEventHandler,
    private val inCallReactionsRepository: InCallReactionsRepository,
    private val callRepository: CallRepository,
    private val buttonActionHandler: ButtonActionHandler,
    private val messageCompositeEditHandler: MessageCompositeEditHandler,
    private val callingMessageHandler: CallingMessageHandler,
    private val resolveLinkPreviewImages: LinkPreviewImagesResolver,
    private val pollVoteHandler: PollVoteHandler,
    private val selfUserId: UserId,
    baseLogger: KaliumLogger = kaliumLogger,
) : ApplicationMessageHandler {

    private val logger by lazy { baseLogger.withFeatureId(ApplicationFlow.EVENT_RECEIVER) }

    @Suppress("ComplexMethod", "LongMethod")
    override suspend fun handleContent(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        messageInstant: Instant,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable,
        targeted: Boolean,
        transient: Boolean
    ) {
        val applicationMessage = MessageUnpackResult.ApplicationMessage(
            conversationId,
            messageInstant,
            senderUserId,
            senderClientId,
            content,
            targeted,
            transient
        )
        when (content.messageContent) {
            is MessageContent.Regular -> {
                val message = applicationMessage.toRegularMessage(selfUserId)
                if (message.isAcceptedDelivery()) processMessage(message) else rejectDelivery(message)
            }

            is MessageContent.Signaling -> {
                val signalingMessage = applicationMessage.toSignalingMessage(selfUserId)
                if (signalingMessage.isAcceptedDelivery()) {
                    processSignaling(transactionContext, signalingMessage)
                } else {
                    rejectDelivery(signalingMessage)
                }
            }
        }
    }

    private fun rejectDelivery(message: Message.Sendable) {
        logger.w(
            "Rejected ${message.content.getType()} message received as targeted = ${message.targeted}, " +
                "transient = ${message.transient}: ${message.toLogString()}"
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun processSignaling(transactionContext: CryptoTransactionContext, signaling: Message.Signaling) {
        when (val content = signaling.content) {
            MessageContent.Ignored -> {
                logger.i(message = "Ignored Signaling Message received: ${signaling.content.getType()}")
            }

            is MessageContent.Availability -> {
                logger.i(message = "Availability status update received: ${content.status}")
                userRepository.updateOtherUserAvailabilityStatus(signaling.senderUserId, content.status)
            }

            is MessageContent.ClientAction -> handleClientAction(signaling)

            is MessageContent.Reaction -> persistReaction(content, signaling.conversationId, signaling.senderUserId, signaling.date)
            is MessageContent.DeleteMessage -> deleteMessageHandler(content, signaling.conversationId, signaling.senderUserId)
            is MessageContent.DeleteForMe -> deleteForMeHandler.handle(signaling, content)
            is MessageContent.Calling -> {
                logger.d("MessageContent.Calling")
                callingMessageHandler.handle(message = signaling, content = content)
            }
            is MessageContent.TextEdited -> editTextHandler.handle(signaling, content)
            is MessageContent.LastRead -> lastReadContentHandler.handle(signaling, content)
            is MessageContent.Cleared -> clearConversationContentHandler.handle(transactionContext, signaling, content)
            is MessageContent.Receipt -> receiptMessageHandler.handle(signaling, content)
            is MessageContent.ButtonAction -> buttonActionHandler.handle(
                signaling.conversationId,
                signaling.senderUserId,
                content.referencedMessageId,
                content.buttonId
            )

            is MessageContent.ButtonActionConfirmation -> {
                buttonActionConfirmationHandler.handle(
                    signaling.conversationId,
                    signaling.senderUserId,
                    content
                )
            }

            is MessageContent.PollVote -> pollVoteHandler.handle(signaling, content)

            is MessageContent.DataTransfer -> dataTransferEventHandler.handle(signaling, content)
            is MessageContent.InCallHandRaise -> callRepository.updateHand(
                signaling.conversationId,
                CallHandIdentity(signaling.senderUserId, signaling.senderClientId.value),
                content.isRaised,
                signaling.date,
                signaling.id
            )
            is MessageContent.InCallEmoji -> inCallReactionsRepository.addInCallReaction(
                conversationId = signaling.conversationId,
                senderUserId = signaling.senderUserId,
                emojis = content.emojis.keys,
            )

            is MessageContent.CompositeEdited -> messageCompositeEditHandler.handle(signaling, content)

            is MessageContent.MultipartEdited -> editMultipartHandler.handle(signaling, content)

            is MessageContent.History -> logUnsupportedHistoryMessage(content)
        }
    }

    private fun logUnsupportedHistoryMessage(content: MessageContent.History) {
        logger.logStructuredJson(
            level = KaliumLogLevel.WARN,
            leadingMessage = "Unsupported history message skipped",
            jsonStringKeyValues = mapOf(
                "outcome" to "skipped",
                "reason" to "unsupported",
                "messageType" to content.getType(),
            )
        )
    }

    private suspend fun handleClientAction(
        signaling: Message.Signaling,
    ) {
        logger.i(message = "ClientAction status update received: ")

        val message = signaling.cryptoSessionResetMessage()

        logger.i(message = "Persisting crypto session reset system message..")
        persistMessage(message)
    }

    private suspend fun processMessage(message: Message.Regular) {
        logger.i(message = "Message received: { \"message\" : ${message.toLogString()} }")
        when (val content = message.content) {
            is MessageContent.Text -> handleTextMessage(message, content)
            is MessageContent.FailedDecryption -> persistMessage(message)
            is MessageContent.Knock -> persistMessage(message)
            is MessageContent.Asset -> {
                try {
                    assetMessageHandler.handle(message)
                } catch (exception: IllegalStateException) {
                    logger.e(
                        "AssetMessageHandler failed for messageId=${message.id} conversationId=${message.conversationId}",
                        exception
                    )
                }
            }

            is MessageContent.RestrictedAsset -> {
                /* no-op */
            }

            is MessageContent.Unknown -> {
                logger.i(message = "Unknown Message received: { \"message\" : ${message.toLogString()} }")
                persistMessage(message)
            }

            is MessageContent.Composite -> persistMessage(message)
            is MessageContent.Poll -> persistMessage(message)
            is MessageContent.Location -> persistMessage(message)
            is MessageContent.Multipart -> handleMultipartMessage(message, content)
        }
    }

    private suspend fun handleMultipartMessage(
        message: Message.Regular,
        messageContent: MessageContent.Multipart
    ) {
        val quotedReference = messageContent.quotedMessageReference
        val adjustedQuoteReference = if (quotedReference != null) {
            verifyMessageQuote(quotedReference, message)
        } else {
            messageContent.quotedMessageReference
        }
        val adjustedMessage = message.copy(
            content = messageContent.copy(quotedMessageReference = adjustedQuoteReference)
        )
        persistMessage(adjustedMessage)
    }

    private suspend fun handleTextMessage(
        message: Message.Regular,
        messageContent: MessageContent.Text
    ) {
        val adjustedMessage = prepareReceivedTextMessage(message, messageDAO, selfUserId, messageEncoder).getOrElse(
            message.copy(
                content = messageContent.copy(
                    quotedMessageReference = messageContent.quotedMessageReference?.copy(isVerified = false)
                )
            )
        )
        persistMessage(adjustedMessage).onSuccess {
            resolveLinkPreviewImages(adjustedMessage.conversationId, adjustedMessage.id)
        }
    }

    private suspend fun verifyMessageQuote(
        quotedReference: MessageContent.QuoteReference,
        message: Message.Regular
    ): MessageContent.QuoteReference =
        // Keep the app's existing unverified-quote fallback; NSE callers propagate storage failure.
        verifyMessageQuote(quotedReference, message.conversationId, messageDAO, selfUserId, messageEncoder)
            .getOrElse(quotedReference.copy(isVerified = false))

    @Suppress("LongParameterList")
    override suspend fun handleDecryptionError(
        eventId: String,
        conversationId: ConversationId,
        messageInstant: Instant,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: MessageContent.FailedDecryption
    ) {
        val message = decryptionFailureMessage(
            eventId = eventId,
            conversationId = conversationId,
            messageInstant = messageInstant,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            content = content,
            selfUserId = selfUserId,
        )
        processMessage(message)
    }

    override suspend fun flushPendingSideEffects() {
        lastReadContentHandler.flushPendingLastReads()
    }
}
