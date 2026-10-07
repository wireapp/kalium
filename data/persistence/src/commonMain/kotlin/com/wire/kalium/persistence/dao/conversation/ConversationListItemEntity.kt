/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.persistence.SelectConversationListOlder
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.message.MessageMapper
import com.wire.kalium.persistence.dao.message.MessagePreviewEntity
import kotlin.time.Instant

data class ConversationListPositionEntity(val activityTime: Instant, val conversationId: QualifiedIDEntity)

data class ConversationListQueryEntity(
    val source: ConversationFilterEntity = ConversationFilterEntity.ALL,
    val searchQuery: String = "",
    val fromArchive: Boolean = false,
    val onlyInteractionEnabled: Boolean = false,
    val strictMlsFilter: Boolean = true,
    val unreadOnly: Boolean = false,
)

data class ConversationListItemEntity(
    val position: ConversationListPositionEntity,
    val type: ConversationEntity.Type,
    val name: String?,
    val mutedStatus: ConversationEntity.MutedStatus,
    val legalHoldStatus: ConversationEntity.LegalHoldStatus,
    val channelAccess: ConversationEntity.ChannelAccess?,
    val isSelfUserMember: Boolean,
    val otherUser: ConversationListUserEntity?,
    val unreadMessages: Int,
    val unreadMentions: Int,
    val unreadReplies: Int,
    val unreadMissedCalls: Int,
    val unreadKnocks: Int,
    val draftText: String?,
    val lastMessage: MessagePreviewEntity?,
    val adminlessGroupDeletionTimestamp: Instant? = null,
)

data class ConversationListUserEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val avatarAssetId: QualifiedIDEntity?,
    val accentId: Int,
    val availabilityStatus: UserAvailabilityStatusEntity?,
    val userType: UserTypeEntity?,
    val connectionStatus: ConnectionEntity.State?,
    val deleted: Boolean,
    val defederated: Boolean,
)

internal fun SelectConversationListOlder.toListItem() = ConversationListItemEntity(
    position = ConversationListPositionEntity(activityTime, conversationId),
    type = type,
    name = name,
    mutedStatus = mutedStatus,
    legalHoldStatus = legalHoldStatus,
    adminlessGroupDeletionTimestamp = adminlessGroupDeletionTimestamp,
    channelAccess = channelAccess,
    isSelfUserMember = isSelfUserMember,
    otherUser = otherUserId?.let { id ->
        ConversationListUserEntity(
            id, userName, avatarAssetId, accentId ?: 0, availabilityStatus, userType,
            connectionStatus, userDeleted ?: false, userDefederated ?: false,
        )
    },
    unreadMessages = unreadMessages.toInt(),
    unreadMentions = unreadMentions.toInt(),
    unreadReplies = unreadReplies.toInt(),
    unreadMissedCalls = unreadMissedCalls.toInt(),
    unreadKnocks = unreadKnocks.toInt(),
    draftText = draftText?.takeUnless { it.isBlank() },
    lastMessage = messageId?.let { id ->
        MessageMapper.toPreviewEntity(
            id = id,
            conversationId = conversationId,
            contentType = requireNotNull(contentType),
            visibility = requireNotNull(visibility),
            senderUserId = requireNotNull(senderUserId),
            isEphemeral = isEphemeral,
            senderName = senderName,
            senderConnectionStatus = senderConnectionStatus,
            senderIsDeleted = senderIsDeleted,
            selfUserId = selfUserId,
            isSelfMessage = isSelfMessage,
            memberChangeList = memberChangeList,
            memberChangeType = memberChangeType,
            updateConversationName = updateConversationName,
            conversationName = name,
            isMentioningSelfUser = isMentioningSelfUser,
            isQuotingSelfUser = isQuotingSelfUser,
            text = messageText,
            assetMimeType = assetMimeType,
            // Notification fields are unused by the shared preview mapper.
            isUnread = false,
            shouldNotify = 0,
            mutedStatus = mutedStatus,
            conversationType = type,
        )
    },
)
