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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.remoteBackup.MessageSyncTracker
import com.wire.kalium.persistence.dao.message.InsertMessageResult
import io.mockative.Mockable

/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */
@Mockable
interface PersistMessageUseCase {
    suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit>
}

internal class PersistMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val selfUserId: UserId,
    private val notificationEventsManager: NotificationEventsManager,
    private val messageSyncTracker: MessageSyncTracker
) : PersistMessageUseCase {
    override suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
        val modifiedMessage = getExpectsReadConfirmationFromMessage(message)
        val isSelfSender = message.isSelfTheSender(selfUserId)

        return messageRepository.persistMessage(
            message = modifiedMessage,
            updateConversationModifiedDate = MessageContentOrderPolicy.shouldUpdateConversationOrder(message.content)
        ).onSuccess {
            val isConversationMuted = it == InsertMessageResult.INSERTED_INTO_MUTED_CONVERSATION

            if (!isConversationMuted && !isSelfSender && message.content.shouldNotifyUser()) {
                notificationEventsManager.scheduleRegularNotificationChecking()
            }

            // Track message for synchronization
            // Only track messages that are not self-sent with Pending status
            // Self-sent pending messages will be tracked after successful sending
            val shouldTrack = !isSelfSender || modifiedMessage.status != Message.Status.Pending
            if (shouldTrack) {
                messageSyncTracker.trackMessageInsert(modifiedMessage)
            }
        }.map { }
    }

    private fun Message.isSelfTheSender(selfUserId: UserId) = senderUserId == selfUserId

    private suspend fun getExpectsReadConfirmationFromMessage(message: Message.Standalone) =
        if (message is Message.Regular) {
            val expectsReadConfirmation: Boolean = messageRepository
                .getReceiptModeFromGroupConversationByQualifiedID(message.conversationId)
                .fold({
                    message.expectsReadConfirmation
                }, { receiptMode ->
                    receiptMode == Conversation.ReceiptMode.ENABLED
                })

            message.copy(expectsReadConfirmation = expectsReadConfirmation)
        } else {
            message
        }

    @Suppress("ComplexMethod")
    private fun MessageContent.shouldNotifyUser(): Boolean =
        when (this) {
            is MessageContent.Text,
            is MessageContent.Asset,
            is MessageContent.Knock,
            is MessageContent.RestrictedAsset,
            is MessageContent.MissedCall,
            is MessageContent.Location,
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
            is MessageContent.TeamMemberRemoved,
            is MessageContent.DataTransfer,
            is MessageContent.InCallEmoji,
            is MessageContent.History,
            is MessageContent.NewConversationWithCellMessage,
            is MessageContent.ConversationAppsEnabledChanged,
            is MessageContent.NewConversationWithCellSelfDeleteDisabledMessage -> false
        }
}
