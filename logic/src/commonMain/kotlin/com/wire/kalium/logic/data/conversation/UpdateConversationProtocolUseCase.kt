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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.Mockable

/**
 * Use case responsible for updating the protocol of a conversation, either locally or remotely.
 *
 * If `localOnly` is true, the protocol is updated only in the local database.
 * Otherwise, the change is performed through the backend, and the updated conversation
 * is persisted locally using [PersistConversationsUseCase] if needed.
 *
 * @param conversationId ID of the conversation to update.
 * @param protocol The new protocol to apply.
 * @param localOnly If true, applies the protocol update locally without backend interaction.
 * @return [Either.Right] with `true` if the update was successful, or [Either.Left] if an error occurred.
 */
@Mockable
interface UpdateConversationProtocolUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        protocol: Conversation.Protocol,
        localOnly: Boolean
    ): Either<CoreFailure, Boolean>
}

@OptIn(ConversationPersistenceApi::class)
internal class UpdateConversationProtocolUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase
) : UpdateConversationProtocolUseCase {

    override suspend fun invoke(
        conversationId: ConversationId,
        protocol: Conversation.Protocol,
        localOnly: Boolean
    ): Either<CoreFailure, Boolean> {
        return if (localOnly) {
            conversationRepository.updateProtocolLocally(conversationId, protocol)
        } else {
            conversationRepository.updateProtocolRemotely(
                conversationId = conversationId,
                protocol = protocol
            )
        }
            .flatMap { status ->
                if (status.hasUpdated) {
                    return@flatMap Either.Right(true)
                }
                persistConversations(listOf(status.response), invalidateMembers = true)
                    .map { true }
            }
    }
}
