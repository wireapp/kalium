/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import kotlinx.coroutines.flow.first

/** Applies synced policy to both a new Pending text and one recovered after process death. */
internal class RefreshPendingTextPolicy(
    private val messages: MessageRepository,
    private val properties: UserPropertyRepository,
    private val selfDeleteTimer: ObserveSelfDeletionTimerSettingsForConversationUseCase,
) {
    suspend operator fun invoke(message: Message.Sendable): Either<CoreFailure, Message.Sendable> {
        if (message !is Message.Regular || !message.canRefreshPolicy()) return Either.Right(message)

        val timer = selfDeleteTimer(message.conversationId, true).first().duration
        val personalReceipts = properties.getReadReceiptsStatus()
        return messages.getReceiptModeFromGroupConversationByQualifiedID(message.conversationId).flatMap { mode ->
            val receipts = mode?.let { it == Conversation.ReceiptMode.ENABLED } ?: personalReceipts
            // UPDATE only. A deletion racing this preparation must never turn into an INSERT.
            // The sender rechecks visibility/status under the same transaction queue as cancellation.
            messages.updatePendingTextPolicy(message.conversationId, message.id, timer?.inWholeMilliseconds, receipts).map {
                message.copy(expirationData = timer?.let { Message.ExpirationData(it) }, expectsReadConfirmation = receipts)
            }
        }
    }

    private fun Message.Regular.canRefreshPolicy(): Boolean {
        val isPendingText = content is MessageContent.Text && status == Message.Status.Pending && isSelfMessage
        val isVisibleUnedited = editStatus == Message.EditStatus.NotEdited && visibility == Message.Visibility.VISIBLE
        val deletionStatus = expirationData?.selfDeletionStatus
        val deletionNotStarted = deletionStatus == null || deletionStatus == Message.ExpirationData.SelfDeletionStatus.NotStarted
        return isPendingText && isVisibleUnedited && deletionNotStarted
    }
}
