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
    @Suppress("LongParameterList", "LongMethod", "FunctionParameterNaming")
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
        mls_cipher_suite: ConversationEntity.CipherSuite,
        mls_epoch: Long,
        mls_group_id: String?,
        mls_last_keying_material_update_date: Instant,
        mls_group_state: ConversationEntity.GroupState,
        access_list: List<ConversationEntity.Access>,
        access_role_list: List<ConversationEntity.AccessRole>,
        mls_proposal_timer: String?,
        muted_time: Long,
        creator_id: String,
        receipt_mode: ConversationEntity.ReceiptMode,
        message_timer: Long?,
        user_message_timer: Long?,
        incomplete_metadata: Boolean,
        archived: Boolean,
        archived_date_time: Instant?,
        mls_verification_status: ConversationEntity.VerificationStatus,
        proteus_verification_status: ConversationEntity.VerificationStatus,
        legal_hold_status: ConversationEntity.LegalHoldStatus,
        is_channel: Boolean,
        channel_access: ConversationEntity.ChannelAccess?,
        channel_add_permission: ConversationEntity.ChannelAddPermission?,
        selfUserId: QualifiedIDEntity?,
        interactionEnabled: Long,
        isFavorite: Boolean,
        folderId: String?,
        folderName: String?,
        wireCell: String?,
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
        lastMessageIsEphemeral: Boolean,
        lastMessageSenderName: String?,
        lastMessageSenderConnectionStatus: ConnectionEntity.State?,
        lastMessageSenderIsDeleted: Boolean?,
        lastMessageIsSelfMessage: Boolean,
        lastMessageMemberChangeList: List<QualifiedIDEntity>?,
        lastMessageMemberChangeType: MessageEntity.MemberChangeType?,
        lastMessageUpdateConversationName: String?,
        lastMessageIsMentioningSelfUser: Boolean,
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
            mlsCipherSuite = mls_cipher_suite,
            mlsEpoch = mls_epoch,
            mlsGroupId = mls_group_id,
            mlsLastKeyingMaterialUpdateDate = mls_last_keying_material_update_date,
            mlsGroupState = mls_group_state,
            accessList = access_list,
            accessRoleList = access_role_list,
            mlsProposalTimer = mls_proposal_timer,
            mutedTime = muted_time,
            creatorId = creator_id,
            receiptMode = receipt_mode,
            messageTimer = message_timer,
            userMessageTimer = user_message_timer,
            incompleteMetadata = incomplete_metadata,
            archived = archived,
            archivedDateTime = archived_date_time,
            mlsVerificationStatus = mls_verification_status,
            proteusVerificationStatus = proteus_verification_status,
            legalHoldStatus = legal_hold_status,
            selfUserId = selfUserId,
            interactionEnabled = interactionEnabled,
            isFavorite = isFavorite,
            folderId = folderId,
            folderName = folderName,
            isChannel = is_channel,
            channelAccess = channel_access,
            channelAddPermission = channel_add_permission,
            wireCell = wireCell,
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
