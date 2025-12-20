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

package com.wire.kalium.logic.data.message

import com.wire.kalium.persistence.dao.message.MessageEntity

/**
 * Utility for determining which message content types should trigger conversation order updates.
 *
 * This policy is used by both real-time message persistence and backup restoration to ensure
 * consistent behavior in conversation list ordering.
 */
object MessageContentOrderPolicy {

    /**
     * Determines if a message with the given content type should update the conversation's last modified date,
     * thereby affecting conversation list order.
     *
     * Content types that represent user interactions and content (text, assets, knocks, etc.) return true.
     * Ephemeral events like reactions, receipts, and most system messages return false.
     */
    @Suppress("ComplexMethod")
    fun shouldUpdateConversationOrder(content: MessageContent): Boolean =
        when (content) {
            is MessageContent.MemberChange.Added -> true
            is MessageContent.MemberChange.Removed -> false
            is MessageContent.Text -> true
            is MessageContent.Calling -> true
            is MessageContent.Asset -> true
            is MessageContent.Knock -> true
            is MessageContent.DeleteMessage -> false
            is MessageContent.TextEdited -> false
            is MessageContent.CompositeEdited -> false
            is MessageContent.MultipartEdited -> false
            is MessageContent.RestrictedAsset -> true
            is MessageContent.DeleteForMe -> false
            is MessageContent.Unknown -> false
            is MessageContent.Availability -> false
            is MessageContent.FailedDecryption -> true
            is MessageContent.MissedCall -> true
            is MessageContent.Ignored -> false
            is MessageContent.LastRead -> false
            is MessageContent.Reaction -> false
            is MessageContent.Cleared -> false
            is MessageContent.ConversationRenamed -> true
            is MessageContent.Receipt -> false
            is MessageContent.ClientAction -> false
            is MessageContent.CryptoSessionReset -> false
            is MessageContent.NewConversationReceiptMode -> false
            is MessageContent.ConversationReceiptModeChanged -> false
            is MessageContent.HistoryLost -> false
            is MessageContent.HistoryLostProtocolChanged -> false
            is MessageContent.ConversationMessageTimerChanged -> false
            is MessageContent.MemberChange.CreationAdded -> false
            is MessageContent.MemberChange.FailedToAdd -> false
            is MessageContent.ConversationCreated -> false
            is MessageContent.MLSWrongEpochWarning -> false
            MessageContent.ConversationDegradedMLS -> false
            MessageContent.ConversationVerifiedMLS -> false
            MessageContent.ConversationDegradedProteus -> false
            MessageContent.ConversationVerifiedProteus -> false
            is MessageContent.Composite -> true
            is MessageContent.ButtonAction -> false
            is MessageContent.ButtonActionConfirmation -> false
            is MessageContent.MemberChange.FederationRemoved -> false
            is MessageContent.FederationStopped.ConnectionRemoved -> false
            is MessageContent.FederationStopped.Removed -> false
            is MessageContent.ConversationProtocolChanged -> false
            is MessageContent.ConversationProtocolChangedDuringACall -> false
            is MessageContent.ConversationStartedUnverifiedWarning -> false
            is MessageContent.Location -> true
            is MessageContent.LegalHold -> false
            is MessageContent.MemberChange.RemovedFromTeam -> false
            is MessageContent.TeamMemberRemoved -> false
            is MessageContent.DataTransfer -> false
            is MessageContent.InCallEmoji -> false
            is MessageContent.Multipart -> true
            is MessageContent.History -> false
            is MessageContent.NewConversationWithCellMessage -> false
            is MessageContent.NewConversationWithCellSelfDeleteDisabledMessage -> false
            is MessageContent.ConversationAppsEnabledChanged -> false
        }

    /**
     * Returns the list of MessageEntity.ContentType values that should trigger conversation order updates.
     *
     * This list is used for efficient SQL filtering when batch updating conversation modified dates,
     * allowing the database to filter qualifying messages directly rather than checking each message
     * in application code.
     *
     * @return List of content types that correspond to shouldUpdateConversationOrder() returning true
     */
    fun getQualifyingContentTypes(): List<MessageEntity.ContentType> {
        return listOf(
            MessageEntity.ContentType.TEXT,
            MessageEntity.ContentType.ASSET,
            MessageEntity.ContentType.KNOCK,
            MessageEntity.ContentType.RESTRICTED_ASSET,
            MessageEntity.ContentType.FAILED_DECRYPTION,
            MessageEntity.ContentType.MISSED_CALL,
            MessageEntity.ContentType.CONVERSATION_RENAMED,
            MessageEntity.ContentType.COMPOSITE,
            MessageEntity.ContentType.LOCATION,
            MessageEntity.ContentType.MEMBER_CHANGE,  // Includes Added
            MessageEntity.ContentType.MULTIPART
        )
    }
}
