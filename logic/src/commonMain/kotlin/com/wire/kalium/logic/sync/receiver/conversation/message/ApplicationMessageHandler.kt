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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.data.message.hasValidData
import com.wire.kalium.logic.data.message.hasValidRemoteData
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.sync.receiver.asset.AssetMessageHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionHandler
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
import com.wire.kalium.util.string.toHexString
import io.mockative.Mockable
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Mockable
internal interface ApplicationMessageHandler {

    @Suppress("LongParameterList")
    suspend fun handleContent(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        messageInstant: Instant,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable,
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
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ApplicationMessageHandlerImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val assetMessageHandler: AssetMessageHandler,
    private val callManagerImpl: Lazy<CallManager>,
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
    private val buttonActionHandler: ButtonActionHandler,
    private val messageCompositeEditHandler: MessageCompositeEditHandler,
    private val messageThreadRepository: MessageThreadRepository,
    private val selfUserId: UserId,
) : ApplicationMessageHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(ApplicationFlow.EVENT_RECEIVER) }

    @Suppress("ComplexMethod", "LongMethod")
    override suspend fun handleContent(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        messageInstant: Instant,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable
    ) {
        when (val protoContent = content.messageContent) {
            is MessageContent.Regular -> {
                val visibility = when (protoContent) {
                    is MessageContent.Unknown -> if (protoContent.hidden) Message.Visibility.HIDDEN else Message.Visibility.VISIBLE
                    is MessageContent.Text -> Message.Visibility.VISIBLE
                    is MessageContent.Asset -> Message.Visibility.VISIBLE
                    is MessageContent.Knock -> Message.Visibility.VISIBLE
                    is MessageContent.RestrictedAsset -> Message.Visibility.VISIBLE
                    is MessageContent.FailedDecryption -> Message.Visibility.VISIBLE
                    is MessageContent.Composite -> Message.Visibility.VISIBLE
                    is MessageContent.Location -> Message.Visibility.VISIBLE
                    is MessageContent.Multipart -> Message.Visibility.VISIBLE
                }
                val message = Message.Regular(
                    id = content.messageUid,
                    content = protoContent,
                    conversationId = conversationId,
                    date = messageInstant,
                    senderUserId = senderUserId,
                    senderClientId = senderClientId,
                    status = Message.Status.Sent,
                    editStatus = Message.EditStatus.NotEdited,
                    visibility = visibility,
                    expectsReadConfirmation = content.expectsReadConfirmation,
                    isSelfMessage = senderUserId == selfUserId,
                    expirationData = content.expiresAfterMillis?.let {
                        Message.ExpirationData(
                            expireAfter = it.toDuration(DurationUnit.MILLISECONDS),
                            selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                        )
                    }
                )
                processMessage(message, content.threadId)
            }

            is MessageContent.Signaling -> {
                val signalingMessage = Message.Signaling(
                    id = content.messageUid,
                    content = protoContent,
                    conversationId = conversationId,
                    date = messageInstant,
                    senderUserId = senderUserId,
                    senderClientId = senderClientId,
                    status = Message.Status.Sent,
                    isSelfMessage = senderUserId == selfUserId,
                    expirationData = content.expiresAfterMillis?.let {
                        Message.ExpirationData(
                            expireAfter = it.toDuration(DurationUnit.MILLISECONDS),
                            selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.NotStarted
                        )
                    }
                )
                processSignaling(transactionContext, signalingMessage)
            }
        }
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
                callManagerImpl.value.onCallingMessageReceived(
                    message = signaling,
                    content = content,
                )
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

            is MessageContent.DataTransfer -> dataTransferEventHandler.handle(signaling, content)
            is MessageContent.InCallEmoji -> inCallReactionsRepository.addInCallReaction(
                conversationId = signaling.conversationId,
                senderUserId = signaling.senderUserId,
                emojis = content.emojis.keys,
            )

            is MessageContent.CompositeEdited -> messageCompositeEditHandler.handle(signaling, content)

            is MessageContent.MultipartEdited -> editMultipartHandler.handle(signaling, content)

            is MessageContent.History -> TODO("HISTORY CLIENTS ARE NOT HANDLED YET")
        }
    }

    private suspend fun handleClientAction(
        signaling: Message.Signaling,
    ) {
        logger.i(message = "ClientAction status update received: ")

        val message = Message.System(
            id = signaling.id,
            content = MessageContent.CryptoSessionReset,
            conversationId = signaling.conversationId,
            date = signaling.date,
            senderUserId = signaling.senderUserId,
            status = signaling.status,
            senderUserName = signaling.senderUserName,
            expirationData = null
        )

        logger.i(message = "Persisting crypto session reset system message..")
        persistMessage(message)
    }

    private suspend fun processMessage(message: Message.Regular, threadId: String?) {
        logger.i(message = "Message received: { \"message\" : ${message.toLogString()} }")
        when (val content = message.content) {
            is MessageContent.Text -> handleTextMessage(message, content, threadId)
            is MessageContent.FailedDecryption -> persistRegularMessage(message, threadId)
            is MessageContent.Knock -> persistRegularMessage(message, threadId)
            is MessageContent.Asset -> {
                try {
                    assetMessageHandler.handle(message)
                    upsertThreadItemIfNeeded(message, threadId)
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
                persistRegularMessage(message, threadId)
            }

            is MessageContent.Composite -> persistRegularMessage(message, threadId)
            is MessageContent.Location -> persistRegularMessage(message, threadId)
            is MessageContent.Multipart -> handleMultipartMessage(message, content, threadId)
        }
    }

    private suspend fun handleMultipartMessage(
        message: Message.Regular,
        messageContent: MessageContent.Multipart,
        threadId: String?,
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
        persistRegularMessage(adjustedMessage, threadId)
    }

    private suspend fun handleTextMessage(
        message: Message.Regular,
        messageContent: MessageContent.Text,
        threadId: String?,
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
        persistRegularMessage(adjustedMessage, threadId)
    }

    private suspend fun persistRegularMessage(message: Message.Regular, threadId: String?) {
        persistMessage(message).onSuccess {
            upsertThreadItemIfNeeded(message, threadId)
        }
    }

    private suspend fun upsertThreadItemIfNeeded(message: Message.Regular, threadId: String?) {
        val resolvedThreadId = threadId ?: return
        messageThreadRepository.upsertThreadReplyIfNeeded(
            conversationId = message.conversationId,
            messageId = message.id,
            threadId = resolvedThreadId,
            creationDate = message.date,
            visibility = message.visibility,
        )
        inferThreadRootIfNeeded(message, resolvedThreadId)
    }

    private suspend fun inferThreadRootIfNeeded(message: Message.Regular, threadId: String) {
        val rootMessageId = threadId
        messageThreadRepository.getThreadByRootMessage(
            conversationId = message.conversationId,
            rootMessageId = rootMessageId,
        ).fold(
            { failure ->
                if (failure is StorageFailure.DataNotFound) {
                    return@fold
                }
                logger.e(
                    "Failed to read thread root mapping for inferred root. " +
                        "conversation=${message.conversationId} threadId=$threadId failure=$failure"
                )
                return
            },
            { existingRoot ->
                if (existingRoot != null) return
            }
        )

        val rootCreationDate = messageRepository
            .getMessageById(message.conversationId, rootMessageId)
            .getOrNull()
            ?.let {
                it.date to ((it as? Message.Standalone)?.visibility ?: Message.Visibility.VISIBLE)
            }
            ?: (message.date to Message.Visibility.VISIBLE)

        messageThreadRepository.upsertThreadRoot(
            conversationId = message.conversationId,
            rootMessageId = rootMessageId,
            threadId = threadId,
            createdAt = message.date,
        ).onFailure {
            logger.e("Failed to infer thread root for conversation=${message.conversationId} threadId=$threadId")
        }

        messageThreadRepository.upsertThreadItem(
            conversationId = message.conversationId,
            messageId = rootMessageId,
            threadId = threadId,
            isRoot = true,
            creationDate = rootCreationDate.first,
            visibility = rootCreationDate.second,
        ).onFailure {
            logger.e("Failed to infer thread root item for conversation=${message.conversationId} threadId=$threadId")
        }
    }

    private suspend fun verifyMessageQuote(
        quotedReference: MessageContent.QuoteReference,
        message: Message.Regular
    ): MessageContent.QuoteReference {
        val quotedMessageSha256 = quotedReference.quotedMessageSha256 ?: run {
            logger.i("Quote message received with null hash. Marking as unverified.")
            return quotedReference.copy(isVerified = false)
        }

        val originalHash =
            messageRepository.getMessageById(message.conversationId, quotedReference.quotedMessageId).map { originalMessage ->
                messageEncoder.encodeMessageContent(originalMessage.date, originalMessage.content)
            }.getOrElse(null)

        return if (quotedMessageSha256.contentEquals(originalHash?.sha256Digest)) {
            quotedReference.copy(isVerified = true)
        } else {
            logger.d("Expected hash = ${originalHash?.sha256Digest?.toHexString()}")
            logger.d("Received hash = ${quotedMessageSha256.toHexString()}")
            logger.i("Quote message received but original doesn't match or wasn't found. Marking as unverified.")
            quotedReference.copy(isVerified = false)
        }
    }

    @Suppress("LongParameterList")
    override suspend fun handleDecryptionError(
        eventId: String,
        conversationId: ConversationId,
        messageInstant: Instant,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: MessageContent.FailedDecryption
    ) {
        val message = Message.Regular(
            id = eventId,
            content = content,
            conversationId = conversationId,
            date = messageInstant,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            status = Message.Status.Sent,
            editStatus = Message.EditStatus.NotEdited,
            visibility = Message.Visibility.VISIBLE,
            isSelfMessage = senderUserId == selfUserId
        )
        processMessage(message, threadId = null)
    }
}

@Deprecated(
    "This will be moved to another package",
    ReplaceWith("com.wire.kalium.logic.data.message.hasValidRemoteData")
)
internal fun AssetContent.hasValidRemoteData() = hasValidRemoteData()

@Deprecated(
    "This will be moved to another package",
    ReplaceWith("com.wire.kalium.logic.data.message.hasValidData")
)
internal fun AssetContent.RemoteData.hasValidData() = hasValidData()
