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

/**
 * Marks conversations in one or all conversations as notified, so the notifications for these messages won't show up again.
 * @see GetNotificationsUseCase
 */
@Suppress("konsist.classesWithUseCaseSuffixShouldHaveSinglePublicOperatorFunctionCalledInvoke")
class MarkMessagesAsNotifiedUseCase internal constructor(
    private val conversationRepository: ConversationRepository
) {

    /**
     * @param conversationId the specific conversation that needs to be marked as notified,
     * or null for marking all notifications as notified.
     */
    @Deprecated("This will be removed in order to use a more explicit input", ReplaceWith("invoke(UpdateTarget)"))
    suspend operator fun invoke(conversationId: ConversationId?): Result = if (conversationId == null) {
        invoke(UpdateTarget.AllConversations)
    } else {
        invoke(UpdateTarget.SingleConversation(conversationId))
    }

    /**
     * @param conversationsToUpdate which conversation(s) to be marked as notified.
     */
    suspend operator fun invoke(conversationsToUpdate: UpdateTarget): Result =
        when (conversationsToUpdate) {
            UpdateTarget.AllConversations -> conversationRepository.updateAllConversationsNotificationDate()

            is UpdateTarget.SingleConversation ->
                conversationRepository.updateConversationNotificationDate(conversationsToUpdate.conversationId)
        }.fold({ Result.Failure(it) }) { Result.Success }

    /**
     * Specifies which conversations should be marked as notified
     */
    sealed interface UpdateTarget {
        /**
         * All conversations should be marked as notified.
         */
        data object AllConversations : UpdateTarget

        /**
         * A specific conversation, represented by its [conversationId], should be marked as notified
         */
        data class SingleConversation(val conversationId: ConversationId) : UpdateTarget
    }
}

sealed class Result {
    data object Success : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
