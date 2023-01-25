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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.PersistReactionUseCase
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.message.ClearConversationContentHandler
import com.wire.kalium.logic.sync.receiver.message.DeleteForMeHandler
import com.wire.kalium.logic.sync.receiver.message.LastReadContentHandler
import com.wire.kalium.logic.sync.receiver.message.MessageTextEditHandler
import com.wire.kalium.logic.sync.receiver.message.ReceiptMessageHandler
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.util.string.toHexString

internal interface ApplicationMessageHandler {

    suspend fun handleContent(
        conversationId: ConversationId,
        timestampIso: String,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable,
    )

    @Suppress("LongParameterList")
    suspend fun handleDecryptionError(
        eventId: String,
        conversationId: ConversationId,
        timestampIso: String,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: MessageContent.FailedDecryption
    )
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ApplicationMessageHandlerImpl(
    private val userRepository: UserRepository,
    private val assetRepository: AssetRepository,
    private val messageRepository: MessageRepository,
    private val userConfigRepository: UserConfigRepository,
    private val callManagerImpl: Lazy<CallManager>,
    private val persistMessage: PersistMessageUseCase,
    private val persistReaction: PersistReactionUseCase,
    private val editTextHandler: MessageTextEditHandler,
    private val lastReadContentHandler: LastReadContentHandler,
    private val clearConversationContentHandler: ClearConversationContentHandler,
    private val deleteForMeHandler: DeleteForMeHandler,
    private val messageEncoder: MessageContentEncoder,
    private val receiptMessageHandler: ReceiptMessageHandler
) : ApplicationMessageHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(ApplicationFlow.EVENT_RECEIVER) }

    @Suppress("ComplexMethod")
    override suspend fun handleContent(
        conversationId: ConversationId,
        timestampIso: String,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: ProtoContent.Readable
    ) {
        when (val protoContent = content.messageContent) {
            is MessageContent.Regular -> {
                val visibility = when (protoContent) {
                    is MessageContent.DeleteMessage -> Message.Visibility.HIDDEN
                    is MessageContent.TextEdited -> Message.Visibility.HIDDEN
                    is MessageContent.DeleteForMe -> Message.Visibility.HIDDEN
                    is MessageContent.Unknown -> if (protoContent.hidden) Message.Visibility.HIDDEN else Message.Visibility.VISIBLE
                    is MessageContent.Text -> Message.Visibility.VISIBLE
                    is MessageContent.Calling -> Message.Visibility.VISIBLE
                    is MessageContent.Asset -> Message.Visibility.VISIBLE
                    is MessageContent.Knock -> Message.Visibility.VISIBLE
                    is MessageContent.RestrictedAsset -> Message.Visibility.VISIBLE
                    is MessageContent.FailedDecryption -> Message.Visibility.VISIBLE
                    is MessageContent.LastRead -> Message.Visibility.HIDDEN
                    is MessageContent.Cleared -> Message.Visibility.HIDDEN
                }
                val message = Message.Regular(
                    id = content.messageUid,
                    content = protoContent,
                    conversationId = conversationId,
                    date = timestampIso,
                    senderUserId = senderUserId,
                    senderClientId = senderClientId,
                    status = Message.Status.SENT,
                    editStatus = Message.EditStatus.NotEdited,
                    visibility = visibility,
                    expectsReadConfirmation = content.expectsReadConfirmation
                )
                processMessage(message)
            }

            is MessageContent.Signaling -> {
                val signalingMessage = Message.Signaling(
                    content.messageUid,
                    protoContent,
                    conversationId,
                    timestampIso,
                    senderUserId,
                    senderClientId,
                    status = Message.Status.SENT
                )
                processSignaling(signalingMessage)
            }
        }
    }

    private fun updateAssetMessageWithDecryptionKeys(
        persistedMessage: Message.Regular,
        remoteData: AssetContent.RemoteData
    ): Message.Regular {
        val assetMessageContent = persistedMessage.content as MessageContent.Asset
        // The message was previously received with just metadata info, so let's update it with the raw data info
        return persistedMessage.copy(
            content = assetMessageContent.copy(
                value = assetMessageContent.value.copy(
                    remoteData = remoteData
                )
            ),
            visibility = Message.Visibility.VISIBLE
        )
    }

    private suspend fun isSenderVerified(messageId: String, conversationId: ConversationId, senderUserId: UserId): Boolean {
        var verified = false
        messageRepository.getMessageById(
            messageUuid = messageId, conversationId = conversationId
        ).onSuccess {
            verified = senderUserId == it.senderUserId
        }
        return verified
    }

    private suspend fun processSignaling(signaling: Message.Signaling) {
        when (val content = signaling.content) {
            MessageContent.Ignored -> {
                logger.i(message = "Ignored Signaling Message received: $signaling")
            }

            is MessageContent.Availability -> {
                logger.i(message = "Availability status update received: ${content.status}")
                userRepository.updateOtherUserAvailabilityStatus(signaling.senderUserId, content.status)
            }

            is MessageContent.ClientAction -> {
                logger.i(message = "ClientAction status update received: ")

                val message = Message.System(
                    id = signaling.id,
                    content = MessageContent.CryptoSessionReset,
                    conversationId = signaling.conversationId,
                    date = signaling.date,
                    senderUserId = signaling.senderUserId,
                    status = signaling.status,
                    senderUserName = signaling.senderUserName
                )

                logger.i(message = "Persisting crypto session reset system message..")
                persistMessage(message)
            }

            is MessageContent.Reaction -> persistReaction(content, signaling.conversationId, signaling.senderUserId, signaling.date)
            is MessageContent.DeleteMessage -> handleDeleteMessage(content, signaling.conversationId, signaling.senderUserId)
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
            is MessageContent.Cleared -> clearConversationContentHandler.handle(signaling, content)
            is MessageContent.Receipt -> receiptMessageHandler.handle(signaling, content)
        }
    }

    @Suppress("ComplexMethod")
    private suspend fun processMessage(message: Message.Regular) {
        logger.i(message = "Message received: { \"message\" : $message }")

        when (val content = message.content) {
            // Persist Messages - > lists
            is MessageContent.Text -> handleTextMessage(message, content)

            is MessageContent.FailedDecryption -> {
                persistMessage(message)
            }

            is MessageContent.Knock -> handleKnock(message)
            is MessageContent.Asset -> handleAssetMessage(message, content)

            is MessageContent.Unknown -> {
                logger.i(message = "Unknown Message received: $message")
                persistMessage(message)
            }

            is MessageContent.RestrictedAsset -> TODO()
        }
    }

    private suspend fun handleKnock(message: Message.Regular) {
        persistMessage(message)
    }

    private suspend fun handleTextMessage(
        message: Message.Regular,
        messageContent: MessageContent.Text
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

    private suspend fun handleAssetMessage(message: Message.Regular, messageContent: MessageContent.Asset) {
        userConfigRepository.isFileSharingEnabled().onSuccess {
            if (it.isFileSharingEnabled != null && it.isFileSharingEnabled) {
                processNonRestrictedAssetMessage(message)
            } else {
                val newMessage = message.copy(
                    content = MessageContent.RestrictedAsset(
                        messageContent.value.mimeType, messageContent.value.sizeInBytes, messageContent.value.name ?: ""
                    )
                )
                persistMessage(newMessage)
            }
        }
    }

    private suspend fun processNonRestrictedAssetMessage(message: Message.Regular) {
        val assetContent = message.content as MessageContent.Asset
        val isPreviewMessage = assetContent.value.sizeInBytes > 0 && !assetContent.value.hasValidRemoteData()
        messageRepository.getMessageById(message.conversationId, message.id).onFailure {
            // No asset message was received previously, so just persist the preview of the asset message
            val isValidImage = assetContent.value.metadata?.let {
                it is AssetContent.AssetMetadata.Image && it.width > 0 && it.height > 0
            } ?: false

            // Web/Mac clients split the asset message delivery into 2. One with the preview metadata (assetName, assetSize...) and
            // with empty encryption keys and the second with empty metadata but all the correct encryption keys. We just want to
            // hide the preview of generic asset messages with empty encryption keys as a way to avoid user interaction with them.
            val previewMessage = message.copy(
                content = message.content.copy(value = assetContent.value),
                visibility = if (isPreviewMessage && !isValidImage) Message.Visibility.HIDDEN else Message.Visibility.VISIBLE
            )
            persistMessage(previewMessage)
        }.onSuccess { persistedMessage ->
            val validDecryptionKeys = message.content.value.remoteData
            // Check the second asset message is from the same original sender
            if (isSenderVerified(
                    persistedMessage.id,
                    persistedMessage.conversationId,
                    message.senderUserId
                ) && persistedMessage is Message.Regular
            ) {
                // The second asset message received from Web/Mac clients contains the full asset decryption keys, so we need to update
                // the preview message persisted previously with the rest of the data
                persistMessage(
                    updateAssetMessageWithDecryptionKeys(
                        persistedMessage, validDecryptionKeys
                    )
                )
            }
        }
    }

    private suspend fun handleDeleteMessage(
        content: MessageContent.DeleteMessage,
        conversationId: ConversationId,
        senderUserId: UserId
    ) {
        if (isSenderVerified(content.messageId, conversationId, senderUserId)) {
            messageRepository.getMessageById(conversationId, content.messageId).onSuccess { messageToRemove ->
                (messageToRemove.content as? MessageContent.Asset)?.value?.remoteData?.let { assetToRemove ->
                    assetRepository.deleteAssetLocally(assetId = assetToRemove.assetId)
                        .onFailure {
                            logger.withFeatureId(ApplicationFlow.ASSETS).w("delete messageToRemove asset locally failure: $it")
                        }
                }
            }
            messageRepository.markMessageAsDeleted(
                messageUuid = content.messageId, conversationId = conversationId
            )
        } else logger.i(message = "Delete message sender is not verified: $content")
    }

    @Suppress("LongParameterList")
    override suspend fun handleDecryptionError(
        eventId: String,
        conversationId: ConversationId,
        timestampIso: String,
        senderUserId: UserId,
        senderClientId: ClientId,
        content: MessageContent.FailedDecryption
    ) {
        val message = Message.Regular(
            id = eventId,
            content = content,
            conversationId = conversationId,
            date = timestampIso,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            status = Message.Status.SENT,
            editStatus = Message.EditStatus.NotEdited,
            visibility = Message.Visibility.VISIBLE
        )
        processMessage(message)
    }
}

fun AssetContent.hasValidRemoteData() = this.remoteData.let {
    it.assetId.isNotEmpty() && it.sha256.isNotEmpty() && it.otrKey.isNotEmpty()
}
