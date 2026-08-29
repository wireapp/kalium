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
import com.wire.kalium.messaging.hooks.NoOpPersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.persistence.dao.message.InsertMessageResult
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.common.error.wrapStorageRequest

/** Message repository operations required by incoming-message persistence. */
public interface EventMessageRepository {
    public suspend fun persistMessage(
        message: Message.Standalone,
        updateConversationModifiedDate: Boolean = false,
    ): Either<CoreFailure, InsertMessageResult>

    public suspend fun getReceiptModeFromGroupConversationByQualifiedID(
        conversationId: ConversationId
    ): Either<CoreFailure, Conversation.ReceiptMode?>
}

/** DAO-backed incoming-message persistence shared by continuous and bounded event processing. */
public class EventMessageRepositoryImpl public constructor(
    private val messageDAO: MessageDAO,
    selfUserId: UserId,
    private val messageMapper: EventMessageEntityMapper = EventMessageEntityMapperImpl(selfUserId),
) : EventMessageRepository {
    override suspend fun persistMessage(
        message: Message.Standalone,
        updateConversationModifiedDate: Boolean,
    ): Either<CoreFailure, InsertMessageResult> = wrapStorageRequest {
        messageDAO.insertOrIgnoreMessage(
            messageMapper.fromMessageToEntity(message),
            updateConversationModifiedDate,
        )
    }

    override suspend fun getReceiptModeFromGroupConversationByQualifiedID(
        conversationId: ConversationId,
    ): Either<CoreFailure, Conversation.ReceiptMode?> = wrapStorageRequest {
        messageDAO.getReceiptModeFromGroupConversationByQualifiedID(conversationId.toDao())?.toModel()
    }

    private fun ConversationEntity.ReceiptMode.toModel(): Conversation.ReceiptMode = when (this) {
        ConversationEntity.ReceiptMode.DISABLED -> Conversation.ReceiptMode.DISABLED
        ConversationEntity.ReceiptMode.ENABLED -> Conversation.ReceiptMode.ENABLED
    }
}

/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */
public fun interface PersistMessageUseCase {
    public suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit>
}

public class PersistMessageUseCaseImpl public constructor(
    private val messageRepository: EventMessageRepository,
    private val selfUserId: UserId,
    private val notificationEventsManager: NotificationEventsManager,
    private val persistMessageHookNotifier: PersistenceEventHookNotifier = NoOpPersistenceEventHookNotifier
) : PersistMessageUseCase {
    override suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
        val modifiedMessage = getExpectsReadConfirmationFromMessage(message)
        val isSelfSender = message.isSelfTheSender(selfUserId)

        return messageRepository.persistMessage(
            message = modifiedMessage,
            updateConversationModifiedDate = message.content.shouldUpdateConversationOrder()
        ).onSuccess {
            val isConversationMuted = it == InsertMessageResult.INSERTED_INTO_MUTED_CONVERSATION

            if (!isConversationMuted && !isSelfSender && message.content.shouldNotifyUser()) {
                notificationEventsManager.scheduleRegularNotificationChecking()
            }

            persistMessageHookNotifier.onMessagePersisted(
                modifiedMessage.toPersistedMessageData(),
                selfUserId
            )
        }.map { }
    }

    private fun Message.Standalone.toPersistedMessageData() = PersistedMessageData(
        conversationId = conversationId,
        messageId = id,
        content = content,
        date = date,
        expireAfter = expirationData?.expireAfter
    )

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

    @Suppress("ComplexMethod", "LongMethod")
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
            is MessageContent.MemberChange.UserPromotedToAdmin -> true
            is MessageContent.TeamMemberRemoved -> false
            is MessageContent.DataTransfer -> false
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
            is MessageContent.MemberChange.UserPromotedToAdmin,
            is MessageContent.TeamMemberRemoved,
            is MessageContent.DataTransfer,
            is MessageContent.InCallEmoji,
            is MessageContent.History,
            is MessageContent.NewConversationWithCellMessage,
            is MessageContent.ConversationAppsEnabledChanged,
            is MessageContent.NewConversationWithCellSelfDeleteDisabledMessage,
            is MessageContent.CellEditorAccessMessage,
            is MessageContent.CellViewerAccessMessage -> false
        }
}
