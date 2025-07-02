/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import io.mockative.Mockable

/**
 * Use case responsible for persisting a single conversation, if it doesn't already exist
 * or if its group state is not yet fully established.
 *
 * Internally checks for an existing conversation in the local storage.
 * If the conversation is new or not fully established (for MLS-capable types),
 * it delegates to [PersistConversationsUseCase] to persist it.
 *
 * @param conversation The conversation to evaluate and persist.
 * @param originatedFromEvent Whether the call originates from an event (affects MLS group state logic).
 * @return [Either.Right] with `true` if the conversation was persisted, `false` otherwise.
 *         Returns [Either.Left] if an error occurred.
 */
@Mockable
internal interface PersistConversationUseCase {
    suspend operator fun invoke(
        conversation: ConversationResponse,
        originatedFromEvent: Boolean = false,
    ): Either<CoreFailure, Boolean>
}

internal class PersistConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase,
) : PersistConversationUseCase {

    override suspend fun invoke(
        conversation: ConversationResponse,
        originatedFromEvent: Boolean
    ): Either<CoreFailure, Boolean> {
        val existingConversation = conversationRepository.getConversationDetails(conversation.id.toModel()).getOrNull()
        val isNewConversation = existingConversation?.let { conversationDetails ->
            (conversationDetails.protocol as? Conversation.ProtocolInfo.MLSCapable)?.groupState?.let { groupState ->
                groupState != Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
            } ?: false
        } ?: true

        if (isNewConversation) {
            persistConversations(listOf(conversation), false, originatedFromEvent)
                .onSuccess {
                    kaliumLogger.withFeatureId(CONVERSATIONS)
                        .d("Persisted new conversation: ${conversation.id}")
                }
        }
        return Either.Right(isNewConversation)
    }

}
