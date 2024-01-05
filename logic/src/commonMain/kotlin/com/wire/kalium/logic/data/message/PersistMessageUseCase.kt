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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold

/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */
interface PersistMessageUseCase {
    suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit>
}

internal class PersistMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val selfUserId: UserId
) : PersistMessageUseCase {
    override suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
        val modifiedMessage = getExpectsReadConfirmationFromMessage(message)

        return messageRepository.persistMessage(
            message = modifiedMessage,
            updateConversationReadDate = message.isSelfTheSender(selfUserId),
            updateConversationModifiedDate = message.content.shouldUpdateConversationOrder()
        )
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
    private fun MessageContent.shouldUpdateConversationOrder(): Boolean =
        when (this) {
            is MessageContent.MemberChange.Added -> true
            is MessageContent.MemberChange.Removed -> false
            is MessageContent.Text -> true
            is MessageContent.Calling -> true
            is MessageContent.Asset -> true
            is MessageContent.Knock -> true
            is MessageContent.DeleteMessage -> false
            is MessageContent.TextEdited -> false
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
        }
}
