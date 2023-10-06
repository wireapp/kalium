/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

internal interface TypingIndicatorRepository {
    suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<ExpiringUserTyping>>

    suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class TypingIndicatorRepositoryImpl(
    private val userPropertyRepository: UserPropertyRepository,
    private val typingIndicatorOutgoingEventManager: TypingIndicatorOutgoingEventManager,
    private val typingIndicatorIncomingEventManager: TypingIndicatorIncomingEventManager
) : TypingIndicatorRepository {

    override suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        if (userPropertyRepository.getTypingIndicatorStatus()) {
            typingIndicatorIncomingEventManager.addTypingUserInConversation(conversationId, userId)
        }
    }

    override suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        typingIndicatorIncomingEventManager.removeTypingUserInConversation(conversationId, userId)
    }

    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<ExpiringUserTyping>> {
        return typingIndicatorIncomingEventManager.observeUsersTyping(conversationId)
    }

    override suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit> {
        if (userPropertyRepository.getTypingIndicatorStatus()) {
            when (typingStatus) {
                Conversation.TypingIndicatorMode.STARTED ->
                    typingIndicatorOutgoingEventManager.sendStartedAndEnqueueStoppingEvent(conversationId)

                Conversation.TypingIndicatorMode.STOPPED -> typingIndicatorOutgoingEventManager.sendStoppingEvent(conversationId)
            }
        }
        return Either.Right(Unit)
    }

    companion object {
        const val BUFFER_SIZE = 32 // drop after this threshold
    }
}

data class ExpiringUserTyping(
    val userId: UserId,
    val date: Instant
) {
    override fun equals(other: Any?): Boolean {
        return other != null && when (other) {
            is ExpiringUserTyping -> other.userId == this.userId
            else -> false
        }
    }

    override fun hashCode(): Int {
        return this.userId.hashCode()
    }
}
