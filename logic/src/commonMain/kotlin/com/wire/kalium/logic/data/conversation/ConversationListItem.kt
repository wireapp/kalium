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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessagePreviewContent
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.UserTypeInfo
import kotlin.time.Instant

public data class ConversationListQuery(
    public val source: ConversationListSource = ConversationListSource.ALL,
    public val searchQuery: String = "",
    public val fromArchive: Boolean = false,
    public val onlyInteractionEnabled: Boolean = false,
    public val strictMlsFilter: Boolean = true,
    public val unreadOnly: Boolean = false,
)

public enum class ConversationListSource {
    ALL,
    GROUPS,
    CHANNELS,
    ONE_ON_ONE,
}

public data class ConversationListPosition(
    public val activityTime: Instant,
    public val conversationId: ConversationId,
)

public sealed interface ConversationListItem {
    public val conversationId: ConversationId
    public val activityTime: Instant

    public val position: ConversationListPosition
        get() = ConversationListPosition(activityTime, conversationId)

    public sealed interface WithMessages : ConversationListItem {
        public val mutedStatus: MutedConversationStatus
        public val legalHoldStatus: Conversation.LegalHoldStatus
        public val unreadState: ConversationListUnreadState
        public val preview: ConversationListPreview?
    }

    public sealed interface Group : WithMessages {
        public val name: String?
        public val isSelfUserMember: Boolean
        public val adminlessGroupDeletionTimestamp: Instant?
    }

    public data class RegularGroup(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        override val name: String?,
        override val isSelfUserMember: Boolean,
        override val mutedStatus: MutedConversationStatus,
        override val legalHoldStatus: Conversation.LegalHoldStatus,
        override val unreadState: ConversationListUnreadState,
        override val preview: ConversationListPreview?,
        override val adminlessGroupDeletionTimestamp: Instant? = null,
    ) : Group

    public data class Channel(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        override val name: String?,
        override val isSelfUserMember: Boolean,
        public val access: ConversationListChannelAccess,
        override val mutedStatus: MutedConversationStatus,
        override val legalHoldStatus: Conversation.LegalHoldStatus,
        override val unreadState: ConversationListUnreadState,
        override val preview: ConversationListPreview?,
        override val adminlessGroupDeletionTimestamp: Instant? = null,
    ) : Group

    public data class OneOnOne(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        public val otherUser: ConversationListUser,
        override val mutedStatus: MutedConversationStatus,
        override val legalHoldStatus: Conversation.LegalHoldStatus,
        override val unreadState: ConversationListUnreadState,
        override val preview: ConversationListPreview?,
    ) : WithMessages

    public data class PendingConnection(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        public val otherUser: ConversationListUser,
    ) : ConversationListItem
}

public data class ConversationListUser(
    public val id: UserId,
    public val name: String?,
    public val avatarAssetId: UserAssetId?,
    public val accentId: Int,
    public val availabilityStatus: UserAvailabilityStatus,
    public val userType: UserTypeInfo,
    public val connectionStatus: ConnectionState,
    public val deleted: Boolean,
    public val defederated: Boolean,
)

public enum class ConversationListChannelAccess {
    PUBLIC,
    PRIVATE,
}

public data class ConversationListUnreadState(
    public val messages: Int = 0,
    public val mentions: Int = 0,
    public val replies: Int = 0,
    public val missedCalls: Int = 0,
    public val knocks: Int = 0,
    public val isManuallyUnread: Boolean = false,
) {
    public val hasUnread: Boolean
        get() = isManuallyUnread || messages > 0 || mentions > 0 || replies > 0 || missedCalls > 0 || knocks > 0
}

public data class ConversationListPreview(
    public val content: MessagePreviewContent,
    public val isSelfMessage: Boolean,
)
