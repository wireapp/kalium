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

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ReceiptModeMapperImpl
import com.wire.kalium.logic.data.conversation.ConversationClear
import com.wire.kalium.logic.data.conversation.conversationClear
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.prepareMultipartEdit
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.cryptoSessionResetMessage
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.data.message.shouldNotifyUser
import com.wire.kalium.logic.data.message.shouldUpdateConversationOrder
import com.wire.kalium.logic.data.message.withGroupReceiptMode
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.updateUserAvailability
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.sync.receiver.conversation.message.prepareReceivedTextMessage
import com.wire.kalium.logic.sync.receiver.conversation.message.toRegularMessage
import com.wire.kalium.logic.sync.receiver.conversation.message.toSignalingMessage
import com.wire.kalium.logic.sync.receiver.conversation.message.verifyMessageQuote
import com.wire.kalium.logic.sync.receiver.handler.LastReadUpdates
import com.wire.kalium.logic.sync.receiver.handler.deleteMessageForMe
import com.wire.kalium.logic.sync.receiver.handler.applyReceivedDeletion
import com.wire.kalium.logic.sync.receiver.handler.receivedDeletion
import com.wire.kalium.logic.sync.receiver.handler.ReceivedMessageDeletion
import com.wire.kalium.logic.sync.receiver.handler.editTextMessage
import com.wire.kalium.logic.sync.receiver.handler.normalizedEmojiSet
import com.wire.kalium.logic.sync.receiver.handler.persistReceipt
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.ReceivedMessage
import com.wire.kalium.persistence.dao.pendingaction.PendingActionEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import com.wire.kalium.persistence.db.MessageEffectDAOs
import kotlin.time.Clock
import kotlinx.serialization.json.Json

/** Ordered canonical effects. The caller owns one SQLite transaction for the whole notification. */
@Suppress("LongParameterList", "TooManyFunctions")
internal class ReceivedMessageHandler(
    private val data: MessageEffectDAOs,
    private val selfUserId: UserId,
    private val mlsEnabled: Boolean,
    private val persistMessage: PersistMessageUseCase,
    private val userConfig: UserConfigRepository,
    private val deleteFile: (String) -> Unit,
    private val previewImages: ReceivedLinkPreviewImages,
    private val calls: MutableList<ReceivedCallEvent>?,
) {
    private val mapper = RegularMessageMapper(selfUserId)
    private val receiptMapper = ReceiptModeMapperImpl()
    private val candidates = mutableListOf<MessageCandidate>()
    private val lastReads = LastReadUpdates(selfUserId)

    fun connection(connection: com.wire.kalium.persistence.dao.ConnectionEntity) {
        if (connection.status == com.wire.kalium.persistence.dao.ConnectionEntity.State.PENDING) {
            candidates.removeAll { it.connectionRequest && it.conversationId == connection.qualifiedConversationId }
            candidates += MessageCandidate("", connection.qualifiedConversationId, connectionRequest = true)
        }
    }

    suspend fun deleteConversation(
        event: com.wire.kalium.logic.data.event.Event.Conversation.DeletedConversation,
        requiredByEarlierPayload: Boolean,
        recreatedLater: Boolean,
        preparedDeletionGroupId: String? = null,
    ) {
        val id = event.conversationId.toDao()
        val conversation = data.conversations.getConversationById(id) ?: return
        val protocol = conversation.protocolInfo
        check(protocol !is ConversationEntity.ProtocolInfo.MLSCapable || protocol.groupId == preparedDeletionGroupId) {
            "MLS conversation deletion requires its group identity after SQL deletion/native rollback"
        }
        check(!recreatedLater) { "Deletion and recreation in one notification require native plaintext replay" }
        check(!requiredByEarlierPayload) {
            "Deletion after earlier conversation effects requires replayable conversation metadata"
        }
        val sender = data.users.getNameAndHandle(event.senderUserId.toDao())
        data.assets.deleteConversationFiles(id, deleteFile)
        (conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable)?.let {
            data.conversations.clearProposalTimer(it.groupId)
        }
        data.conversations.deleteConversationByQualifiedID(id)
        candidates += MessageCandidate(
            event.id, id,
            deletion = ReceivedMessageResult(
                messageId = event.id, conversationValue = id.value, conversationDomain = id.domain,
                text = null, shouldNotify = conversation.type != ConversationEntity.Type.MEETING, mayPreview = true,
                type = ReceivedNotificationType.CONVERSATION_DELETED, conversationName = conversation.name, senderName = sender?.name,
            )
        )
    }

    suspend fun failedDecryption(message: com.wire.kalium.logic.data.message.Message.Regular) {
        val conversationId = message.conversationId.toDao()
        val conversation = checkNotNull(data.conversations.getConversationById(conversationId))
        check(!conversation.hasIncompleteMetadata && conversation.removedBy == null) { "Conversation requires synchronization" }
        // The app stores one failed-decryption row per backend event. SQL insert-or-ignore
        // preserves it if several failed ciphertexts share that event, or it is replayed.
        if (data.messages.getMessageById(message.id, conversationId) != null || migratedMessage(message.id, conversation) != null) return
        val receiptMode = data.messages.getReceiptModeFromGroupConversationByQualifiedID(conversationId)
        val stored = receiptMode?.let { message.withGroupReceiptMode(receiptMapper.fromEntityToModel(it)) } ?: message
        data.messages.insertReceivedMessages(
            listOf(ReceivedMessage(mapper.toEntity(stored), stored.content.shouldUpdateConversationOrder(), enqueueDelivery = false))
        )
    }

    @Suppress("CyclomaticComplexMethod")
    suspend fun handle(
        unpacked: MessageUnpackResult.ApplicationMessage,
        laterMessages: List<MessageUnpackResult.ApplicationMessage>,
        replayingSelfRemoval: Boolean,
        isMLS: Boolean,
        requiredEarlier: Set<QualifiedIDEntity> = emptySet(),
        recreatedLater: Set<QualifiedIDEntity> = emptySet(),
    ) {
        if (unpacked.content.messageContent is MessageContent.LegalHold ||
            unpacked.content.messageContent is MessageContent.InCallEmoji
        ) return
        val conversationId = unpacked.conversationId.toDao()
        val conversation = checkNotNull(data.conversations.getConversationById(conversationId)) {
            "Conversation must already exist"
        }
        check(!conversation.hasIncompleteMetadata && (replayingSelfRemoval || conversation.removedBy == null)) {
            "Conversation requires synchronization"
        }
        // Team leave removes SQL memberships without changing crypto, including for self.
        // Like the app, accept decryptable content until conversation lifecycle processing
        // removes access. Notification.sq also does not require a self-member row.
        // Legal-hold state and embedded legal-hold effects are deliberately app-only.
        when (val content = unpacked.content.messageContent) {
            is MessageContent.Text, is MessageContent.Knock, is MessageContent.Location, is MessageContent.Unknown,
            is MessageContent.Composite, is MessageContent.Asset, is MessageContent.Multipart ->
                storeRegular(unpacked, conversation, laterMessages)
            is MessageContent.Ignored, is MessageContent.History -> Unit
            is MessageContent.ClientAction -> persistMessage(unpacked.toSignalingMessage(selfUserId).cryptoSessionResetMessage()).orThrow()
            is MessageContent.DataTransfer -> trackingIdentifier(unpacked, content)
            is MessageContent.TextEdited, is MessageContent.CompositeEdited, is MessageContent.MultipartEdited -> edit(unpacked)
            is MessageContent.ButtonAction, is MessageContent.ButtonActionConfirmation -> buttonAction(unpacked)
            is MessageContent.DeleteMessage -> delete(unpacked, content.messageId)
            is MessageContent.DeleteForMe -> deleteForMe(content, conversation)
            is MessageContent.Cleared -> clear(unpacked, content, conversation, requiredEarlier, recreatedLater, laterMessages)
            is MessageContent.Reaction -> {
                val current = data.messages.getMessageById(content.messageId, conversationId)
                if (current != null) {
                    requireSupportedRegular(current)
                    data.reactions.updateReactions(
                        content.messageId,
                        conversationId,
                        unpacked.senderUserId.toDao(),
                        unpacked.instant,
                        content.normalizedEmojiSet()
                    )
                }
            }
            is MessageContent.Receipt -> persistReceipt(
                data.messages,
                data.receipts,
                selfUserId,
                unpacked.toSignalingMessage(selfUserId),
                content
            ).orThrow()
            is MessageContent.LastRead -> lastReads.add(
                unpacked.toSignalingMessage(selfUserId),
                content,
                unpacked.senderUserId == selfUserId && isSelfConversation(conversation)
            )
            is MessageContent.LegalHold -> Unit
            is MessageContent.Calling -> handleCalling(unpacked, content, conversation, isMLS)
            is MessageContent.Availability -> updateUserAvailability(data.users, unpacked.senderUserId, content.status)
            else -> error("Message contains unsupported required work")
        }
    }

    private suspend fun handleCalling(
        message: MessageUnpackResult.ApplicationMessage,
        content: MessageContent.Calling,
        carrier: ConversationEntity,
        isMLS: Boolean,
    ) {
        val value = callingJson.decodeFromString<MessageContent.Calling.CallingValue>(content.value)
        // The NSE has no active call. Remote mute, like in-call reactions, has no durable effect.
        if (value.type == "REMOTEMUTE") return
        val calls = checkNotNull(calls) { "Calling event processing is unavailable" }
        // Match the shared app's authorization: only our own devices can address another
        // conversation through the calling payload (for example, rejecting via SELF).
        val targetId = if (message.senderUserId == selfUserId) content.conversationId?.toDao() ?: carrier.id else carrier.id
        val conversation = if (targetId == carrier.id) carrier else {
            checkNotNull(data.conversations.getConversationById(targetId)) { "Calling target must already exist" }
        }
        if (targetId != carrier.id) {
            check(!conversation.hasIncompleteMetadata && conversation.removedBy == null) {
                "Calling target requires synchronization"
            }
        }
        val event = ReceivedCallEvent(
            payload = content.value,
            conversationId = targetId,
            senderId = message.senderUserId.toDao(),
            senderClientId = message.senderClientId.value,
            time = message.instant,
            isGroup = conversation.type.isGroup,
            isMLS = isMLS,
            messageId = message.content.messageUid,
        )
        CallSignalJournal.append(data.metadata, event)
        calls += event
    }

    private suspend fun trackingIdentifier(unpacked: MessageUnpackResult.ApplicationMessage, content: MessageContent.DataTransfer) {
        if (unpacked.senderUserId != selfUserId) return
        val identifier = content.trackingIdentifier?.identifier ?: return
        val current = data.config.getTrackingIdentifier()
        if (current != identifier) {
            current?.let { data.config.setPreviousTrackingIdentifier(it) }
            data.config.setTrackingIdentifier(identifier)
        }
    }

    private suspend fun buttonAction(unpacked: MessageUnpackResult.ApplicationMessage) {
        val conversationId = unpacked.conversationId.toDao()
        when (val content = unpacked.content.messageContent) {
            is MessageContent.ButtonAction -> if (unpacked.senderUserId == selfUserId) {
                data.composites.markAsSelected(content.referencedMessageId, conversationId, content.buttonId)
            }
            is MessageContent.ButtonActionConfirmation -> {
                val current = data.messages.getMessageById(content.referencedMessageId, conversationId)
                if (current?.senderUserId == unpacked.senderUserId.toDao()) {
                    val buttonId = content.buttonId
                    if (buttonId != null) {
                        data.composites.markAsSelected(content.referencedMessageId, conversationId, buttonId)
                    } else data.composites.resetSelection(content.referencedMessageId, conversationId)
                }
            }
            else -> error("Expected button action")
        }
    }

    private suspend fun isSelfConversation(conversation: ConversationEntity): Boolean {
        // Match the app's registered self-conversation IDs. Missing prepared state stays incomplete.
        val selfIds = if (conversation.type == ConversationEntity.Type.SELF) {
            listOfNotNull(
                checkNotNull(data.conversations.getSelfConversationId(ConversationEntity.Protocol.PROTEUS)),
                if (mlsEnabled) checkNotNull(data.conversations.getSelfConversationId(ConversationEntity.Protocol.MLS)) else null,
            )
        } else emptyList()
        return conversation.id in selfIds
    }

    private suspend fun delete(unpacked: MessageUnpackResult.ApplicationMessage, messageId: String) {
        val current = data.messages.getMessageById(messageId, unpacked.conversationId.toDao()) ?: return
        val deletion = current.receivedDeletion(unpacked.senderUserId, selfUserId)
        if (deletion != ReceivedMessageDeletion.NONE) {
            data.assets.deleteMessageFiles(current.conversationId, current.id, deleteFile)
        }
        data.messages.applyReceivedDeletion(current.id, current.conversationId, deletion).orThrow()
    }

    private suspend fun deleteForMe(content: MessageContent.DeleteForMe, conversation: ConversationEntity) {
        val inSelfConversation = isSelfConversation(conversation)
        if (inSelfConversation) {
            data.assets.deleteMessageFiles(content.conversationId.toDao(), content.messageId, deleteFile)
        }
        deleteMessageForMe(data.messages, content, inSelfConversation).orThrow()
    }

    private suspend fun clear(
        unpacked: MessageUnpackResult.ApplicationMessage,
        content: MessageContent.Cleared,
        carrier: ConversationEntity,
        requiredEarlier: Set<QualifiedIDEntity>,
        recreatedLater: Set<QualifiedIDEntity>,
        laterMessages: List<MessageUnpackResult.ApplicationMessage>,
    ) {
        val action = conversationClear(unpacked.senderUserId == selfUserId, isSelfConversation(carrier), content.needToRemoveLocally)
        if (action == ConversationClear.IGNORE) return
        if (action == ConversationClear.CONVERSATION) {
            val target = content.conversationId.toDao()
            check(target != carrier.id && target !in requiredEarlier && target !in recreatedLater) {
                "Local removal requires replayable conversation metadata"
            }
        }
        // The account-owned cleanup consumer applies this cutoff, never newer content.
        val type = if (action == ConversationClear.CONVERSATION) PendingActionType.DELETE_CONVERSATION_LOCALLY
            else PendingActionType.CLEAR_CONVERSATION_CONTENT
        val request = PendingActionEntity(
            content.conversationId.toDao(),
            minOf(content.time, unpacked.instant).toEpochMilliseconds()
        )
        data.pendingActions.upsert(type, request.qualifiedId, request.createdAt)
        data.applyConversationCleanup(
            type,
            request,
            deleteFile,
            clearReplayKey(carrier.id, unpacked.content.messageUid, request.qualifiedId),
            preserveConversation = laterMessages.any { it.conversationId.toDao() == request.qualifiedId },
        )
    }

    private suspend fun edit(unpacked: MessageUnpackResult.ApplicationMessage) {
        val content = unpacked.content.messageContent
        val editedId = checkNotNull(content.editTargetId()) { "Expected message edit" }
        val conversationId = unpacked.conversationId.toDao()
        val current = data.messages.getMessageById(editedId, conversationId) ?: return
        if (current.senderUserId != unpacked.senderUserId.toDao()) return
        val regular = requireSupportedRegular(current)
        val newId = unpacked.content.messageUid
        check(newId == current.id || data.messages.getMessageById(newId, conversationId) == null) {
            "Edit message identity conflicts with stored state"
        }
        when (content) {
            is MessageContent.TextEdited ->
                editTextMessage(data.messages, mapper.fromEntity(regular), unpacked.toSignalingMessage(selfUserId), content).orThrow()
            is MessageContent.CompositeEdited ->
                data.messages.updateCompositeMessageContent(conversationId, current.id, unpacked.instant, content.toEntity(), newId)
            is MessageContent.MultipartEdited -> {
                val update = prepareMultipartEdit(mapper.fromEntity(regular), content, unpacked.instant)
                data.messages.updateTextMessageContent(
                    editInstant = update.instant,
                    conversationId = conversationId,
                    currentMessageId = current.id,
                    newTextContent = update.content.toEntity(),
                    newMessageId = newId,
                    updateStatus = if (update.markAsSent) MessageEntity.Status.SENT else null,
                )
            }
            else -> error("Expected message edit")
        }
        rekeyed(current.id, newId, conversationId)
    }

    private fun rekeyed(oldId: String, newId: String, conversationId: QualifiedIDEntity) {
        candidates.indices.forEach { index ->
            val candidate = candidates[index]
            if (candidate.messageId == oldId && candidate.conversationId == conversationId) {
                candidates[index] = candidate.copy(messageId = newId)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun storeRegular(
        unpacked: MessageUnpackResult.ApplicationMessage,
        conversation: ConversationEntity,
        laterMessages: List<MessageUnpackResult.ApplicationMessage>,
    ) {
        val content = unpacked.content.messageContent as MessageContent.Regular
        // On Events replay, a later edit may already have changed the original message's primary key.
        var storedId = unpacked.content.messageUid
        laterMessages.forEach { later ->
            val editedId = later.content.messageContent.editTargetId()
            if (later.conversationId == unpacked.conversationId && later.senderUserId == unpacked.senderUserId &&
                editedId == storedId
            ) {
                storedId = later.content.messageUid
            }
        }
        val original = data.messages.getMessageById(unpacked.content.messageUid, conversation.id)
        val rekeyed = if (original == null && storedId != unpacked.content.messageUid) {
            data.messages.getMessageById(storedId, conversation.id)?.also {
                check(it is MessageEntity.Regular && it.editStatus is MessageEntity.EditStatus.Edited) {
                    "Edit message identity conflicts with stored state"
                }
            }
        } else null
        // A later one-to-one migration can already have moved this payload after a
        // SQL commit/native abort. Its canonical message survives in the target.
        val existing = original ?: rekeyed ?: migratedMessage(storedId, conversation)
        if (existing != null && content !is MessageContent.Asset) {
            val regular = requireSupportedRegular(existing)
            check(existing.senderUserId == unpacked.senderUserId.toDao()) {
                "Received message identity conflicts with stored state"
            }
            candidates += MessageCandidate(existing.id, existing.conversationId)
            if (content is MessageContent.Text) previewImages.resolve(data, mapper.fromEntity(regular))
            return
        }
        // Persist the deadline for the app's expiration executor; NSE does not run deletion timers.
        var message = unpacked.toRegularMessage(selfUserId, selfDeletionStartedAt = Clock.System.now())
        if (content is MessageContent.Asset) message = prepareReceivedAsset(message, existing, mapper, userConfig) ?: return
        if (content is MessageContent.Text) message = prepareReceivedTextMessage(message, data.messages, selfUserId).orThrow()
        if (content is MessageContent.Multipart) {
            content.quotedMessageReference?.let { quote ->
                val verified = verifyMessageQuote(quote, message.conversationId, data.messages, selfUserId).orThrow()
                message = message.copy(content = content.copy(quotedMessageReference = verified))
            }
        }
        // DAO null is the repository's DataNotFound fallback, preserving the sender's flag.
        data.messages.getReceiptModeFromGroupConversationByQualifiedID(conversation.id)?.let {
            message = message.withGroupReceiptMode(receiptMapper.fromEntityToModel(it))
        }
        data.messages.insertReceivedMessages(
            listOf(
                ReceivedMessage(mapper.toEntity(message), message.content.shouldUpdateConversationOrder(), enqueueDelivery = false)
            )
        )
        if (content is MessageContent.Text) previewImages.resolve(data, message)
        candidates += MessageCandidate(
            message.id,
            message.conversationId.toDao(),
            message.id.takeIf { existing == null && unpacked.senderUserId != selfUserId }
        )
    }

    fun moved(sources: List<QualifiedIDEntity>, target: QualifiedIDEntity) {
        candidates.indices.forEach { index ->
            val candidate = candidates[index]
            if (!candidate.connectionRequest && candidate.conversationId in sources) {
                candidates[index] = candidate.copy(conversationId = target)
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun migratedMessage(id: String, source: ConversationEntity): MessageEntity? {
        if (source.type != ConversationEntity.Type.ONE_ON_ONE || source.protocolInfo !is ConversationEntity.ProtocolInfo.Proteus) {
            return null
        }
        val peer = data.members.getConversationMembers(source.id).firstOrNull { it != selfUserId.toDao() } ?: return null
        val target = data.users.getUserDetailsByQualifiedID(peer)?.activeOneOnOneConversationId ?: return null
        val conversation = data.conversations.getConversationById(target) ?: return null
        if (conversation.type != ConversationEntity.Type.ONE_ON_ONE || conversation.protocolInfo !is ConversationEntity.ProtocolInfo.MLS ||
            data.members.getMemberRole(peer, target) == null
        ) return null
        return data.messages.getMessageById(id, target)
    }

    /** Actions alone are silent. New content in the target is selected from its final canonical state. */
    @Suppress("CyclomaticComplexMethod", "LongMethod") // Preserve the ordered completion effects.
    suspend fun finish(): ReceivedMessageResult? {
        val pending = lastReads.snapshot()
        data.conversations.updateReadDatesAndGetHasUnreadEvents(pending.mapKeys { it.key.toDao() })
        lastReads.persisted(pending)
        val presentations = candidates.distinctBy {
            Triple(it.messageId, it.conversationId, it.deletion != null || it.connectionRequest)
        }.mapNotNull { candidate ->
            candidate.deletion?.let {
                return@mapNotNull it
            }
            if (candidate.connectionRequest) {
                val connection = data.connections.getConnection(candidate.conversationId) ?: return@mapNotNull null
                if (connection.status != com.wire.kalium.persistence.dao.ConnectionEntity.State.PENDING ||
                    connection.shouldNotify != true
                ) return@mapNotNull null
                return@mapNotNull ReceivedMessageResult(
                    messageId = "",
                    conversationValue = candidate.conversationId.value,
                    conversationDomain = candidate.conversationId.domain,
                    text = null,
                    shouldNotify = true,
                    mayPreview = true,
                    type = ReceivedNotificationType.CONNECTION_REQUEST,
                    senderName = data.users.getNameAndHandle(connection.qualifiedToId)?.name,
                )
            }
            val (id, conversationId, deliveryId) = candidate
            val message = data.messages.getMessageById(id, conversationId) as? MessageEntity.Regular ?: return@mapNotNull null
            // A message explicitly removed in this notification has no remaining delivery work.
            // Keep the original wire identity when an edit has rekeyed a surviving message.
            val conversation = checkNotNull(data.conversations.getConversationById(conversationId))
            // Like Notification.sq and the app delivery queue, absence of a self-member row
            // does not suppress decryptable content (connection metadata can contain only the peer).
            deliveryId?.let { data.deliveries.enqueue(conversationId, it) }
            if (message.visibility != MessageEntity.Visibility.VISIBLE) return@mapNotNull null
            val lastNotificationDate = conversation.lastNotificationDate
            // Notification.sq excludes multipart even when its mute-policy check permits an alert.
            val notify = message.content !is MessageEntityContent.Multipart &&
                mapper.fromEntity(message).shouldNotifyUser() && !conversation.archived &&
                message.senderUserId != selfUserId.toDao() && message.date > conversation.lastReadDate &&
                (lastNotificationDate == null || message.date > lastNotificationDate) &&
                data.messages.needsToBeNotified(message.id, conversationId)
            // Match Android: verification warnings affect reply eligibility, not message previews.
            val mayPreview = message.expireAfterMs == null
            val text = (message.content as? MessageEntityContent.Text)?.messageBody
            val reason = if (notify) null else when {
                message.content is MessageEntityContent.Multipart -> "MULTIPART"
                !mapper.fromEntity(message).shouldNotifyUser() -> "CONTENT_POLICY"
                conversation.archived -> "ARCHIVED"
                message.senderUserId == selfUserId.toDao() -> "SELF_MESSAGE"
                message.date <= conversation.lastReadDate -> "ALREADY_READ"
                lastNotificationDate != null && message.date <= lastNotificationDate -> "ALREADY_NOTIFIED"
                else -> "NOTIFICATION_POLICY"
            }
            ReceivedMessageResult(
                message.id,
                conversationId.value,
                conversationId.domain,
                text,
                notify,
                mayPreview,
                conversationName = conversation.name,
                isGroupConversation = conversation.type.isGroup,
                senderName = data.users.getNameAndHandle(message.senderUserId)?.name,
                suppressionReason = reason,
                previewPolicyReason = if (mayPreview) null else
                    "expiry=${message.expireAfterMs != null};mls=${conversation.mlsVerificationStatus.name};" +
                        "proteus=${conversation.proteusVerificationStatus.name}"
            )
        }
        return presentations.lastOrNull { it.shouldNotify } ?: presentations.lastOrNull()
    }

    private fun requireSupportedRegular(message: MessageEntity): MessageEntity.Regular {
        // Existing app messages can contain attachments or previews. Actions use their canonical rows.
        check(message is MessageEntity.Regular) { "Action requires a regular message" }
        return message
    }

    private fun MessageContent.editTargetId(): String? = when (this) {
        is MessageContent.TextEdited -> editMessageId
        is MessageContent.CompositeEdited -> editMessageId
        is MessageContent.MultipartEdited -> editMessageId
        else -> null
    }

    private data class MessageCandidate(
        val messageId: String,
        val conversationId: QualifiedIDEntity,
        val deliveryMessageId: String? = null,
        val deletion: ReceivedMessageResult? = null,
        val connectionRequest: Boolean = false,
    )

}

internal fun <T> Either<CoreFailure, T>.orThrow(): T = when (this) {
    is Either.Left -> throw KaliumSyncException("Required notification work failed", value)
    is Either.Right -> value
}

/** Transient result only: never saved in an additional presentation store or logged. */
data class ReceivedMessageResult(
    val messageId: String,
    val conversationValue: String,
    val conversationDomain: String,
    val text: String?,
    val shouldNotify: Boolean,
    val mayPreview: Boolean,
    val type: ReceivedNotificationType = ReceivedNotificationType.MESSAGE,
    val conversationName: String? = null,
    val senderName: String? = null,
    val suppressionReason: String? = null,
    val previewPolicyReason: String? = null,
    val isGroupConversation: Boolean = false,
)

enum class ReceivedNotificationType { MESSAGE, CONVERSATION_DELETED, CONNECTION_REQUEST }

private val callingJson = Json { ignoreUnknownKeys = true }
