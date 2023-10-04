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

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.safeComputeAndMutateSetValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal interface TypingIndicatorRepository {
    suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<ExpiringUserTyping>>

    suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit>
}

internal class TypingIndicatorRepositoryImpl(
    private val incomingTypingEventsCache: ConcurrentMutableMap<ConversationId, MutableSet<ExpiringUserTyping>>,
    private val outgoingStoppedQueueTypingEventsCache: ConcurrentMutableMap<ConversationId, Instant>,
    private val conversationRepository: ConversationRepository,
    private val userPropertyRepository: UserPropertyRepository
) : TypingIndicatorRepository {

    private val incomingTypingUserDataSourceFlow: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        if (userPropertyRepository.getTypingIndicatorStatus()) {
            incomingTypingEventsCache.safeComputeAndMutateSetValue(conversationId) { ExpiringUserTyping(userId, Clock.System.now()) }
                .also {
                    incomingTypingUserDataSourceFlow.tryEmit(Unit)
                }
        }
    }

    override suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        incomingTypingEventsCache.block { entry ->
            entry[conversationId]?.apply { this.removeAll { it.userId == userId } }
        }.also {
            incomingTypingUserDataSourceFlow.tryEmit(Unit)
        }
    }

    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<ExpiringUserTyping>> {
        return incomingTypingUserDataSourceFlow
            .map { incomingTypingEventsCache[conversationId] ?: emptySet() }
            .onStart { emit(incomingTypingEventsCache[conversationId] ?: emptySet()) }
    }

    override suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit> {
        if (userPropertyRepository.getTypingIndicatorStatus()) {
            conversationRepository.sendTypingIndicatorStatus(conversationId, typingStatus)
                .fold({ kaliumLogger.w("Skipping failed to send typing indicator status: $it") }) {
                    when (typingStatus) {
                        Conversation.TypingIndicatorMode.STARTED -> outgoingStoppedQueueTypingEventsCache[conversationId] =
                            Clock.System.now().plus(TYPING_INDICATOR_TIMEOUT_IN_SECONDS)

                        Conversation.TypingIndicatorMode.STOPPED -> outgoingStoppedQueueTypingEventsCache.remove(conversationId)
                    }
                    Either.Right(Unit)
                }
        }
        return Either.Right(Unit)
    }

    companion object {
        const val BUFFER_SIZE = 32 // drop after this threshold
        val TYPING_INDICATOR_TIMEOUT_IN_SECONDS = 5.toDuration(DurationUnit.SECONDS)
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
