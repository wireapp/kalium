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
package com.wire.kalium.persistence.dao.conversation

import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageMapper
import com.wire.kalium.persistence.dao.message.draft.MessageDraftMapper
import com.wire.kalium.persistence.dao.unread.UnreadEventMapper
import kotlinx.datetime.Instant

data object ConversationDetailsWithEventsMapper {
    // suppressed because the method cannot be shortened and there are unused parameters because sql view returns some duplicated fields
    @Suppress("LongParameterList", "LongMethod", "UnusedParameter")
    fun fromViewToModel(
        qualifiedId: QualifiedIDEntity,
        name: String?,
        type: ConversationEntity.Type,
        callStatus: CallEntity.Status?,
        previewAssetId: QualifiedIDEntity?,
        mutedStatus: ConversationEntity.MutedStatus,
        teamId: String?,
        lastModifiedDate: Instant?,
        lastReadDate: Instant,
        userAvailabilityStatus: UserAvailabilityStatusEntity?,
        userType: UserTypeEntity?,
        botService: BotIdEntity?,
        userDeleted: Boolean?,
        userDefederated: Boolean?,
        userSupportedProtocols: Set<SupportedProtocolEntity>?,
        connectionStatus: ConnectionEntity.State?,
        otherUserId: QualifiedIDEntity?,
        otherUserActiveConversationId: QualifiedIDEntity?,
        isActive: Long,
        accentId: Int?,
        lastNotifiedMessageDate: Instant?,
        selfRole: MemberEntity.Role?,
        protocol: ConversationEntity.Protocol,
        mlsCipherSuite: ConversationEntity.CipherSuite,
        mlsEpoch: Long,
        mlsGroupId: String?,
        mlsLastKeyingMaterialUpdateDate: Instant,
        mlsGroupState: ConversationEntity.GroupState,
        accessList: List<ConversationEntity.Access>,
        accessRoleList: List<ConversationEntity.AccessRole>,
        mlsProposalTimer: String?,
        mutedTime: Long,
        creatorId: String,
        receiptMode: ConversationEntity.ReceiptMode,
        messageTimer: Long?,
        userMessageTimer: Long?,
        incompleteMetadata: Boolean,
        archived: Boolean,
        archivedDateTime: Instant?,
        mlsVerificationStatus: ConversationEntity.VerificationStatus,
        proteusVerificationStatus: ConversationEntity.VerificationStatus,
        legalHoldStatus: ConversationEntity.LegalHoldStatus,
        isChannel: Boolean,
        selfUserId: QualifiedIDEntity?,
        interactionEnabled: Long,
        isFavorite: Boolean,
        folderId: String?,
        folderName: String?,
        unreadKnocksCount: Long?,
        unreadMissedCallsCount: Long?,
        unreadMentionsCount: Long?,
        unreadRepliesCount: Long?,
        unreadMessagesCount: Long?,
        hasNewActivitiesToShow: Long,
        messageDraftText: String?,
        messageDraftEditMessageId: String?,
        messageDraftQuotedMessageId: String?,
        messageDraftMentionList: List<MessageEntity.Mention>?,
        lastMessageId: String?,
        lastMessageContentType: MessageEntity.ContentType?,
        lastMessageDate: Instant?,
        lastMessageVisibility: MessageEntity.Visibility?,
        lastMessageSenderUserId: QualifiedIDEntity?,
        lastMessageIsEphemeral: Boolean?,
        lastMessageSenderName: String?,
        lastMessageSenderConnectionStatus: ConnectionEntity.State?,
        lastMessageSenderIsDeleted: Boolean?,
        lastMessageIsSelfMessage: Boolean?,
        lastMessageMemberChangeList: List<QualifiedIDEntity>?,
        lastMessageMemberChangeType: MessageEntity.MemberChangeType?,
        lastMessageUpdateConversationName: String?,
        lastMessageIsMentioningSelfUser: Boolean?,
        lastMessageIsQuotingSelfUser: Boolean?,
        lastMessageText: String?,
        lastMessageAssetMimeType: String?,
    ): ConversationDetailsWithEventsEntity = ConversationDetailsWithEventsEntity(
        conversationViewEntity = ConversationMapper.fromViewToModel(
            qualifiedId = qualifiedId,
            name = name,
            type = type,
            callStatus = callStatus,
            previewAssetId = previewAssetId,
            mutedStatus = mutedStatus,
            teamId = teamId,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = lastReadDate,
            userAvailabilityStatus = userAvailabilityStatus,
            userType = userType,
            botService = botService,
            userDeleted = userDeleted,
            userDefederated = userDefederated,
            userSupportedProtocols = userSupportedProtocols,
            connectionStatus = connectionStatus,
            otherUserId = otherUserId,
            otherUserActiveConversationId = otherUserActiveConversationId,
            isActive = isActive,
            accentId = accentId,
            lastNotifiedMessageDate = lastNotifiedMessageDate,
            selfRole = selfRole,
            protocol = protocol,
            mlsCipherSuite = mlsCipherSuite,
            mlsEpoch = mlsEpoch,
            mlsGroupId = mlsGroupId,
            mlsLastKeyingMaterialUpdateDate = mlsLastKeyingMaterialUpdateDate,
            mlsGroupState = mlsGroupState,
            accessList = accessList,
            accessRoleList = accessRoleList,
            mlsProposalTimer = mlsProposalTimer,
            mutedTime = mutedTime,
            creatorId = creatorId,
            receiptMode = receiptMode,
            messageTimer = messageTimer,
            userMessageTimer = userMessageTimer,
            incompleteMetadata = incompleteMetadata,
            archived = archived,
            archivedDateTime = archivedDateTime,
            mlsVerificationStatus = mlsVerificationStatus,
            proteusVerificationStatus = proteusVerificationStatus,
            legalHoldStatus = legalHoldStatus,
            selfUserId = selfUserId,
            interactionEnabled = interactionEnabled,
            isFavorite = isFavorite,
            folderId = folderId,
            folderName = folderName,
            isChannel = isChannel,
        ),
        unreadEvents = UnreadEventMapper.toConversationUnreadEntity(
            conversationId = qualifiedId,
            knocksCount = unreadKnocksCount,
            missedCallsCount = unreadMissedCallsCount,
            mentionsCount = unreadMentionsCount,
            repliesCount = unreadRepliesCount,
            messagesCount = unreadMessagesCount,
        ),
        lastMessage =
        @Suppress("ComplexCondition") // we need to check all these fields
        if (
            lastMessageId != null && lastMessageContentType != null && lastMessageDate != null
            && lastMessageVisibility != null && lastMessageSenderUserId != null && lastMessageIsEphemeral != null
            && lastMessageIsSelfMessage != null && lastMessageIsMentioningSelfUser != null
        ) {
            MessageMapper.toPreviewEntity(
                id = lastMessageId,
                conversationId = qualifiedId,
                contentType = lastMessageContentType,
                date = lastMessageDate,
                visibility = lastMessageVisibility,
                senderUserId = lastMessageSenderUserId,
                isEphemeral = lastMessageIsEphemeral,
                senderName = lastMessageSenderName,
                senderConnectionStatus = lastMessageSenderConnectionStatus,
                senderIsDeleted = lastMessageSenderIsDeleted,
                selfUserId = selfUserId,
                isSelfMessage = lastMessageIsSelfMessage,
                memberChangeList = lastMessageMemberChangeList,
                memberChangeType = lastMessageMemberChangeType,
                updatedConversationName = lastMessageUpdateConversationName,
                conversationName = name,
                isMentioningSelfUser = lastMessageIsMentioningSelfUser,
                isQuotingSelfUser = lastMessageIsQuotingSelfUser,
                text = lastMessageText,
                assetMimeType = lastMessageAssetMimeType,
                isUnread = lastMessageDate > lastReadDate,
                isNotified = if (lastNotifiedMessageDate?.let { lastMessageDate > it } ?: false) 1 else 0,
                mutedStatus = mutedStatus,
                conversationType = type,
            )
        } else null,
        messageDraft = if (!messageDraftText.isNullOrBlank()) {
            MessageDraftMapper.toDao(
                conversationId = qualifiedId,
                text = messageDraftText,
                editMessageId = messageDraftEditMessageId,
                quotedMessageId = messageDraftQuotedMessageId,
                mentionList = messageDraftMentionList ?: emptyList(),
            )
        } else null,
        hasNewActivitiesToShow = hasNewActivitiesToShow > 0L,
    )
}
