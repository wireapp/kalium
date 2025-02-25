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
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MessageAttachmentsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.UnreadEventsQueries
import com.wire.kalium.persistence.content.ButtonContentQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException

internal fun MessageEntityContent.Asset.hasValidRemoteData() =
    assetId.isNotEmpty() && assetOtrKey.isNotEmpty() && assetSha256Key.isNotEmpty()

internal fun ByteArray?.isNullOrEmpty() = this?.isEmpty() ?: true

/**
 * Explaining that this is mainly used to share a bit of logic between MessageDAO and MigrationDAO
 */
internal interface MessageInsertExtension {
    /**
     * Returns true if the [message] is an asset message that is already in the DB and any of its decryption keys are null/empty. This means
     * that this asset message that is in the DB was only a preview message with valid metadata but no valid keys (Web clients send 2
     * separated messages). Therefore, it still needs to be updated with the valid keys in order to be displayed.
     */
    fun isValidAssetMessageUpdate(message: MessageEntity): Boolean
    fun updateAssetMessage(message: MessageEntity)
    fun contentTypeOf(content: MessageEntityContent): MessageEntity.ContentType
    fun insertMessageOrIgnore(message: MessageEntity)
}

internal class MessageInsertExtensionImpl(
    private val messagesQueries: MessagesQueries,
    private val attachmentQueries: MessageAttachmentsQueries,
    private val unreadEventsQueries: UnreadEventsQueries,
    private val conversationsQueries: ConversationsQueries,
    private val buttonContentQueries: ButtonContentQueries,
    private val selfUserIDEntity: UserIDEntity
) : MessageInsertExtension {

    override fun isValidAssetMessageUpdate(message: MessageEntity): Boolean {
        if (message !is MessageEntity.Regular) return false
        // If asset has no valid keys, no need to query the DB
        val hasValidKeys = message.content is MessageEntityContent.Asset && message.content.hasValidRemoteData()
        val currentMessageHasMissingAssetInformation =
            hasValidKeys && messagesQueries.selectById(message.id, message.conversationId).executeAsList().firstOrNull()?.let {
                val isFromSameSender = message.senderUserId == it.senderUserId
                        && message.senderClientId == it.senderClientId
                (it.assetId.isNullOrEmpty() || it.assetOtrKey.isNullOrEmpty() || it.assetSha256.isNullOrEmpty()) && isFromSameSender
            } ?: false
        return currentMessageHasMissingAssetInformation
    }

    override fun updateAssetMessage(message: MessageEntity) {
        if (message.content !is MessageEntityContent.Asset) {
            return
        }
        val assetMessageContent = message.content as MessageEntityContent.Asset
        with(assetMessageContent) {
            // This will ONLY update the VISIBILITY of the original base message and all the asset content related fields
            messagesQueries.updateAssetContent(
                messageId = message.id,
                conversationId = message.conversationId,
                visibility = message.visibility,
                assetId = assetId,
                assetDomain = assetDomain,
                assetToken = assetToken,
                assetSize = assetSizeInBytes,
                assetMimeType = assetMimeType,
                assetName = assetName,
                assetOtrKey = assetOtrKey,
                assetSha256 = assetSha256Key,
                assetEncryptionAlgorithm = assetEncryptionAlgorithm
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun insertMessageOrIgnore(message: MessageEntity) {
        try {
            insertBaseMessageOrError(message)
            insertMessageContent(message)
            insertUnreadEvent(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            /* no-op */
        }
    }

    private fun insertBaseMessageOrError(message: MessageEntity) {
        // do not add withContext
        messagesQueries.insertMessage(
            id = message.id,
            conversation_id = message.conversationId,
            creation_date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = if (message is MessageEntity.Regular) message.senderClientId else null,
            visibility = message.visibility,
            last_edit_date =
            if (message is MessageEntity.Regular && message.editStatus is MessageEntity.EditStatus.Edited) message.editStatus.lastDate
            else null,
            status = message.status,
            content_type = contentTypeOf(message.content),
            expects_read_confirmation = if (message is MessageEntity.Regular) message.expectsReadConfirmation else false,
            expire_after_millis = if (message is MessageEntity.Regular) message.expireAfterMs else null,
            self_deletion_end_date = if (message is MessageEntity.Regular) message.selfDeletionEndDate else null
        )
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun insertMessageContent(message: MessageEntity) {
        when (val content = message.content) {
            is MessageEntityContent.Text -> {
                messagesQueries.insertMessageTextContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    text_body = content.messageBody,
                    quoted_message_id = content.quotedMessageId,
                    is_quote_verified = content.isQuoteVerified
                )
                content.linkPreview.forEach {
                    messagesQueries.insertMessageLinkPreview(
                        message_id = message.id,
                        conversation_id = message.conversationId,
                        url = it.url,
                        url_offset = it.urlOffset,
                        permanent_url = it.permanentUrl,
                        summary = it.summary,
                        title = it.title
                    )
                }
                content.mentions.forEach {
                    messagesQueries.insertMessageMention(
                        message_id = message.id,
                        conversation_id = message.conversationId,
                        start = it.start,
                        length = it.length,
                        user_id = it.userId
                    )
                }
            }

            is MessageEntityContent.RestrictedAsset -> messagesQueries.insertMessageRestrictedAssetContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                asset_mime_type = content.mimeType,
                asset_size = content.assetSizeInBytes,
                asset_name = content.assetName
            )

            is MessageEntityContent.Asset -> {
                messagesQueries.insertMessageAssetContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    asset_size = content.assetSizeInBytes,
                    asset_name = content.assetName,
                    asset_mime_type = content.assetMimeType,
                    asset_otr_key = content.assetOtrKey,
                    asset_sha256 = content.assetSha256Key,
                    asset_id = content.assetId,
                    asset_token = content.assetToken,
                    asset_domain = content.assetDomain,
                    asset_encryption_algorithm = content.assetEncryptionAlgorithm,
                    asset_width = content.assetWidth,
                    asset_height = content.assetHeight,
                    asset_duration_ms = content.assetDurationMs,
                    asset_normalized_loudness = content.assetNormalizedLoudness
                )
            }

            is MessageEntityContent.Unknown -> messagesQueries.insertMessageUnknownContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                unknown_encoded_data = content.encodedData,
                unknown_type_name = content.typeName
            )

            is MessageEntityContent.FailedDecryption -> messagesQueries.insertFailedDecryptionMessageContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                unknown_encoded_data = content.encodedData,
                error_code = content.code?.toLong()
            )

            is MessageEntityContent.MemberChange -> messagesQueries.insertMemberChangeMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                member_change_list = content.memberUserIdList,
                member_change_type = content.memberChangeType
            )

            is MessageEntityContent.MissedCall -> messagesQueries.insertMissedCallMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                caller_id = message.senderUserId
            )

            is MessageEntityContent.Knock -> {
                /** NO-OP. No need to insert any content for Knock messages */
            }

            is MessageEntityContent.ConversationRenamed -> messagesQueries.insertConversationRenamedMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                conversation_name = content.conversationName
            )

            is MessageEntityContent.TeamMemberRemoved -> {
                /* no-op */
            }

            is MessageEntityContent.CryptoSessionReset -> {
                /* no-op */
            }

            is MessageEntityContent.HistoryLost -> {
                /* no-op */
            }

            is MessageEntityContent.HistoryLostProtocolChanged -> {
                /* no-op */
            }

            is MessageEntityContent.ConversationReceiptModeChanged -> messagesQueries.insertConversationReceiptModeChanged(
                message_id = message.id,
                conversation_id = message.conversationId,
                receipt_mode = content.receiptMode
            )

            is MessageEntityContent.NewConversationReceiptMode -> messagesQueries.insertNewConversationReceiptMode(
                message_id = message.id,
                conversation_id = message.conversationId,
                receipt_mode = content.receiptMode
            )

            is MessageEntityContent.ConversationMessageTimerChanged -> messagesQueries.insertConversationMessageTimerChanged(
                message_id = message.id,
                conversation_id = message.conversationId,
                message_timer = content.messageTimer
            )

            is MessageEntityContent.Composite -> insertCompositeMessage(content, message)
            is MessageEntityContent.ConversationCreated,
            is MessageEntityContent.MLSWrongEpochWarning -> {
                /* no-op */
            }

            is MessageEntityContent.ConversationDegradedMLS -> {
                /* no-op */
            }

            is MessageEntityContent.ConversationVerifiedMLS -> {
                /* no-op */
            }

            is MessageEntityContent.ConversationDegradedProteus -> {
                /* no-op */
            }

            is MessageEntityContent.ConversationVerifiedProteus -> {
                /* no-op */
            }

            is MessageEntityContent.Federation -> {
                messagesQueries.insertFederationTerminatedMessage(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    domain_list = content.domainList,
                    federation_type = content.type
                )
            }

            is MessageEntityContent.ConversationProtocolChanged -> messagesQueries.insertConversationProtocolChanged(
                message_id = message.id,
                conversation_id = message.conversationId,
                protocol = content.protocol
            )

            is MessageEntityContent.ConversationProtocolChangedDuringACall -> messagesQueries.insertConversationProtocolChangedDuringACall(
                message_id = message.id,
                conversation_id = message.conversationId
            )

            is MessageEntityContent.ConversationStartedUnverifiedWarning -> {
                /* no-op */
            }

            is MessageEntityContent.Location -> messagesQueries.insertLocationMessageContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                latitude = content.latitude,
                longitude = content.longitude,
                name = content.name,
                zoom = content.zoom
            )

            is MessageEntityContent.LegalHold -> messagesQueries.insertLegalHoldMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                legal_hold_member_list = content.memberUserIdList,
                legal_hold_type = content.type
            )
        }
    }

    private fun insertUnreadEvent(message: MessageEntity) {
        val lastRead = conversationsQueries.getConversationLastReadDate(message.conversationId).executeAsOneOrNull()
            ?: Instant.DISTANT_PAST

        if (!message.isSelfMessage && message.date > lastRead) {
            when (message.content) {
                is MessageEntityContent.Knock -> unreadEventsQueries.insertEvent(
                    message.id,
                    UnreadEventTypeEntity.KNOCK,
                    message.conversationId,
                    message.date
                )

                is MessageEntityContent.Text -> insertUnreadTextContent(
                    message,
                    message.content as MessageEntityContent.Text
                )

                is MessageEntityContent.Asset,
                is MessageEntityContent.RestrictedAsset,
                is MessageEntityContent.Composite,
                is MessageEntityContent.Location,
                is MessageEntityContent.FailedDecryption -> unreadEventsQueries.insertEvent(
                    message.id,
                    UnreadEventTypeEntity.MESSAGE,
                    message.conversationId,
                    message.date
                )

                MessageEntityContent.MissedCall -> unreadEventsQueries.insertEvent(
                    message.id,
                    UnreadEventTypeEntity.MISSED_CALL,
                    message.conversationId,
                    message.date
                )

                is MessageEntityContent.Unknown,
                MessageEntityContent.ConversationCreated,
                is MessageEntityContent.ConversationMessageTimerChanged,
                is MessageEntityContent.ConversationReceiptModeChanged,
                is MessageEntityContent.ConversationRenamed,
                is MessageEntityContent.ConversationProtocolChanged,
                is MessageEntityContent.ConversationProtocolChangedDuringACall,
                MessageEntityContent.CryptoSessionReset,
                MessageEntityContent.HistoryLost,
                MessageEntityContent.HistoryLostProtocolChanged,
                MessageEntityContent.MLSWrongEpochWarning,
                is MessageEntityContent.MemberChange,
                is MessageEntityContent.NewConversationReceiptMode,
                is MessageEntityContent.Federation,
                MessageEntityContent.ConversationDegradedMLS,
                MessageEntityContent.ConversationVerifiedMLS,
                MessageEntityContent.ConversationDegradedProteus,
                MessageEntityContent.ConversationVerifiedProteus,
                MessageEntityContent.ConversationStartedUnverifiedWarning,
                is MessageEntityContent.TeamMemberRemoved,
                is MessageEntityContent.LegalHold,
                    -> {
                    /* no-op */
                }
            }
        }
    }

    private fun insertUnreadTextContent(message: MessageEntity, textContent: MessageEntityContent.Text) {
        var isQuotingSelfUser = false
        if (textContent.quotedMessageId != null) {
            val senderId = messagesQueries.getMessageSenderId(
                textContent.quotedMessageId,
                message.conversationId
            )
                .executeAsOneOrNull()
            isQuotingSelfUser = senderId == selfUserIDEntity
        }
        when {
            isQuotingSelfUser -> unreadEventsQueries.insertEvent(
                message.id,
                UnreadEventTypeEntity.REPLY,
                message.conversationId,
                message.date
            )

            textContent.mentions.map { it.userId }.contains(selfUserIDEntity) ->
                unreadEventsQueries.insertEvent(
                    message.id,
                    UnreadEventTypeEntity.MENTION,
                    message.conversationId,
                    message.date
                )

            else -> unreadEventsQueries.insertEvent(
                message.id,
                UnreadEventTypeEntity.MESSAGE,
                message.conversationId,
                message.date
            )
        }
    }

    private fun insertCompositeMessage(
        content: MessageEntityContent.Composite,
        message: MessageEntity
    ) {
        content.text?.let { text ->
            messagesQueries.insertMessageTextContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                text_body = text.messageBody,
                quoted_message_id = text.quotedMessageId,
                is_quote_verified = text.isQuoteVerified
            )
        }
        content.buttonList.forEach { button ->
            buttonContentQueries.insertButton(
                message_id = message.id,
                conversation_id = message.conversationId,
                id = button.id,
                text = button.text
            )
        }
    }

    @Suppress("ComplexMethod")
    override fun contentTypeOf(content: MessageEntityContent): MessageEntity.ContentType = when (content) {
        is MessageEntityContent.Text -> MessageEntity.ContentType.TEXT
        is MessageEntityContent.Asset -> MessageEntity.ContentType.ASSET
        is MessageEntityContent.Knock -> MessageEntity.ContentType.KNOCK
        is MessageEntityContent.MemberChange -> MessageEntity.ContentType.MEMBER_CHANGE
        is MessageEntityContent.MissedCall -> MessageEntity.ContentType.MISSED_CALL
        is MessageEntityContent.Unknown -> MessageEntity.ContentType.UNKNOWN
        is MessageEntityContent.FailedDecryption -> MessageEntity.ContentType.FAILED_DECRYPTION
        is MessageEntityContent.RestrictedAsset -> MessageEntity.ContentType.RESTRICTED_ASSET
        is MessageEntityContent.ConversationRenamed -> MessageEntity.ContentType.CONVERSATION_RENAMED
        is MessageEntityContent.TeamMemberRemoved -> MessageEntity.ContentType.REMOVED_FROM_TEAM
        is MessageEntityContent.CryptoSessionReset -> MessageEntity.ContentType.CRYPTO_SESSION_RESET
        is MessageEntityContent.NewConversationReceiptMode -> MessageEntity.ContentType.NEW_CONVERSATION_RECEIPT_MODE
        is MessageEntityContent.ConversationReceiptModeChanged -> MessageEntity.ContentType.CONVERSATION_RECEIPT_MODE_CHANGED
        is MessageEntityContent.HistoryLost -> MessageEntity.ContentType.HISTORY_LOST
        is MessageEntityContent.HistoryLostProtocolChanged -> MessageEntity.ContentType.HISTORY_LOST_PROTOCOL_CHANGED
        is MessageEntityContent.ConversationMessageTimerChanged -> MessageEntity.ContentType.CONVERSATION_MESSAGE_TIMER_CHANGED
        is MessageEntityContent.ConversationCreated -> MessageEntity.ContentType.CONVERSATION_CREATED
        is MessageEntityContent.MLSWrongEpochWarning -> MessageEntity.ContentType.MLS_WRONG_EPOCH_WARNING
        is MessageEntityContent.ConversationDegradedMLS -> MessageEntity.ContentType.CONVERSATION_DEGRADED_MLS
        is MessageEntityContent.ConversationDegradedProteus -> MessageEntity.ContentType.CONVERSATION_DEGRADED_PROTEUS
        is MessageEntityContent.Composite -> MessageEntity.ContentType.COMPOSITE
        is MessageEntityContent.Federation -> MessageEntity.ContentType.FEDERATION
        MessageEntityContent.ConversationVerifiedMLS -> MessageEntity.ContentType.CONVERSATION_VERIFIED_MLS
        MessageEntityContent.ConversationVerifiedProteus -> MessageEntity.ContentType.CONVERSATION_VERIFIED_PROTEUS
        is MessageEntityContent.ConversationProtocolChanged -> MessageEntity.ContentType.CONVERSATION_PROTOCOL_CHANGED
        is MessageEntityContent.ConversationProtocolChangedDuringACall ->
            MessageEntity.ContentType.CONVERSATION_PROTOCOL_CHANGED_DURING_CALL

        is MessageEntityContent.ConversationStartedUnverifiedWarning ->
            MessageEntity.ContentType.CONVERSATION_STARTED_UNVERIFIED_WARNING

        is MessageEntityContent.Location -> MessageEntity.ContentType.LOCATION
        is MessageEntityContent.LegalHold -> MessageEntity.ContentType.LEGAL_HOLD
    }
}
