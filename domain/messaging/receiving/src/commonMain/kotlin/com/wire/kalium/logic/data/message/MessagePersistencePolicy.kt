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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity

fun Message.Regular.withGroupReceiptMode(receiptMode: Conversation.ReceiptMode?): Message.Regular = copy(
    expectsReadConfirmation = receiptMode == Conversation.ReceiptMode.ENABLED
)

@Suppress("ComplexMethod", "LongMethod")
fun MessageContent.shouldUpdateConversationOrder(): Boolean =
    when (this) {
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
        is MessageContent.MarkUnread -> false
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
        is MessageContent.Poll -> true
        is MessageContent.ButtonAction -> false
        is MessageContent.ButtonActionConfirmation -> false
        is MessageContent.PollVote -> false
        is MessageContent.MemberChange.FederationRemoved -> false
        is MessageContent.FederationStopped.ConnectionRemoved -> false
        is MessageContent.FederationStopped.Removed -> false
        is MessageContent.ConversationProtocolChanged -> false
        is MessageContent.ConversationProtocolChangedDuringACall -> false
        is MessageContent.ConversationStartedUnverifiedWarning -> false
        is MessageContent.Location -> true
        is MessageContent.LegalHold -> false
        is MessageContent.MemberChange.RemovedFromTeam -> false
        is MessageContent.MemberChange.UserPromotedToAdmin -> true
        is MessageContent.TeamMemberRemoved -> false
        is MessageContent.DataTransfer -> false
        is MessageContent.InCallHandRaise -> false
        is MessageContent.InCallEmoji -> false
        is MessageContent.Multipart -> true
        is MessageContent.History -> false
        is MessageContent.NewConversationWithCellMessage -> false
        is MessageContent.NewConversationWithCellSelfDeleteDisabledMessage -> false
        is MessageContent.CellEditorAccessMessage -> false
        is MessageContent.CellViewerAccessMessage -> false
        is MessageContent.ConversationAppsEnabledChanged -> false
    }

@Suppress("ComplexMethod", "LongMethod")
fun MessageContent.shouldNotifyUser(): Boolean =
    when (this) {
        is MessageContent.Text,
        is MessageContent.Asset,
        is MessageContent.Knock,
        is MessageContent.RestrictedAsset,
        is MessageContent.MissedCall,
        is MessageContent.Location,
        is MessageContent.Poll,
        is MessageContent.Multipart -> true

        is MessageContent.MemberChange.Added,
        is MessageContent.MemberChange.Removed,
        is MessageContent.Calling,
        is MessageContent.DeleteMessage,
        is MessageContent.TextEdited,
        is MessageContent.CompositeEdited,
        is MessageContent.MultipartEdited,
        is MessageContent.DeleteForMe,
        is MessageContent.Unknown,
        is MessageContent.Availability,
        is MessageContent.FailedDecryption,
        is MessageContent.Ignored,
        is MessageContent.LastRead,
        is MessageContent.MarkUnread,
        is MessageContent.Reaction,
        is MessageContent.Cleared,
        is MessageContent.ConversationRenamed,
        is MessageContent.Receipt,
        is MessageContent.ClientAction,
        is MessageContent.CryptoSessionReset,
        is MessageContent.NewConversationReceiptMode,
        is MessageContent.ConversationReceiptModeChanged,
        is MessageContent.HistoryLost,
        is MessageContent.HistoryLostProtocolChanged,
        is MessageContent.ConversationMessageTimerChanged,
        is MessageContent.MemberChange.CreationAdded,
        is MessageContent.MemberChange.FailedToAdd,
        is MessageContent.ConversationCreated,
        is MessageContent.MLSWrongEpochWarning,
        MessageContent.ConversationDegradedMLS,
        MessageContent.ConversationVerifiedMLS,
        MessageContent.ConversationDegradedProteus,
        MessageContent.ConversationVerifiedProteus,
        is MessageContent.Composite,
        is MessageContent.PollVote,
        is MessageContent.ButtonAction,
        is MessageContent.ButtonActionConfirmation,
        is MessageContent.MemberChange.FederationRemoved,
        is MessageContent.FederationStopped.ConnectionRemoved,
        is MessageContent.FederationStopped.Removed,
        is MessageContent.ConversationProtocolChanged,
        is MessageContent.ConversationProtocolChangedDuringACall,
        is MessageContent.ConversationStartedUnverifiedWarning,
        is MessageContent.LegalHold,
        is MessageContent.MemberChange.RemovedFromTeam,
        is MessageContent.MemberChange.UserPromotedToAdmin,
        is MessageContent.TeamMemberRemoved,
        is MessageContent.DataTransfer,
        is MessageContent.InCallHandRaise,
        is MessageContent.InCallEmoji,
        is MessageContent.History,
        is MessageContent.NewConversationWithCellMessage,
        is MessageContent.ConversationAppsEnabledChanged,
        is MessageContent.NewConversationWithCellSelfDeleteDisabledMessage,
        is MessageContent.CellEditorAccessMessage,
        is MessageContent.CellViewerAccessMessage -> false
    }

/** The app's notification mapper omits ordinary restricted assets, but retains generic ephemeral alerts. */
fun Message.Regular.shouldNotifyUser(): Boolean = content.shouldNotifyUser() &&
    (content !is MessageContent.RestrictedAsset || expirationData != null)

/** Authorized deletion removes content, but retains the canonical identity and author. */
fun MessageEntity.matchesSystemMessage(
    senderUserId: com.wire.kalium.persistence.dao.QualifiedIDEntity,
    content: com.wire.kalium.persistence.dao.message.MessageEntityContent.System,
): Boolean = this.senderUserId == senderUserId &&
    (visibility == MessageEntity.Visibility.DELETED || this is MessageEntity.System && this.content == content)

/** Canonical system identity is also the replay guard; no receiving journal is needed. */
suspend fun MessageDAO.hasPersistedSystemMessage(message: Message.System) = wrapStorageRequest {
    getMessageById(message.id, message.conversationId.toDao())?.let { existing ->
        check(
            existing.matchesSystemMessage(message.senderUserId.toDao(), message.content.toMessageEntityContent())
        ) { "System message identity conflicts with stored state" }
        true
    } ?: false
}
