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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.fold
import kotlinx.datetime.Instant

/**
 * Marks conversations in one or all conversations as notified, so the notifications for these messages won't show up again.
 * @see GetNotificationsUseCase
 */
// todo(interface). extract interface for use case
public class MarkMessagesAsNotifiedUseCase internal constructor(
    private val conversationRepository: ConversationRepository
) {

    /**
     * @param conversationsToUpdate which conversation(s) to be marked as notified.
     */
    public suspend operator fun invoke(conversationsToUpdate: UpdateTarget): Result =
        when (conversationsToUpdate) {
            UpdateTarget.AllConversations -> conversationRepository.updateAllConversationsNotificationDate()

            is UpdateTarget.SingleConversation -> {
                val notifiedDate = conversationsToUpdate.notifiedDate
                if (notifiedDate != null) {
                    conversationRepository.updateConversationNotificationDate(
                        conversationsToUpdate.conversationId,
                        notifiedDate
                    )
                } else {
                    conversationRepository.updateConversationNotificationDate(conversationsToUpdate.conversationId)
                }
            }
        }.fold({ Result.Failure(it) }) { Result.Success }

    /**
     * Specifies which conversations should be marked as notified
     */
    public sealed interface UpdateTarget {
        /**
         * All conversations should be marked as notified.
         */
        public data object AllConversations : UpdateTarget

        /**
         * A specific conversation, represented by its [conversationId], should be marked as notified.
         * @param conversationId The conversation to mark as notified
         * @param notifiedDate The timestamp of the last notified message. When provided, this exact
         * timestamp is used instead of looking up the latest message in the database. This prevents
         * race conditions where new messages arrive between displaying notifications and marking them
         * as notified.
         */
        public data class SingleConversation(
            val conversationId: ConversationId,
            val notifiedDate: Instant? = null
        ) : UpdateTarget
    }
}

public sealed class Result {
    public data object Success : Result()
    public data class Failure(val storageFailure: StorageFailure) : Result()
}
