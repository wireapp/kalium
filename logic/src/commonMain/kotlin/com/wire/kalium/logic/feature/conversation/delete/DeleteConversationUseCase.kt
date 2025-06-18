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
package com.wire.kalium.logic.feature.conversation.delete

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.Mockable

/**
 * Use case responsible for deleting a conversation, handling both Proteus and MLS protocols.
 *
 * - For **Proteus** conversations, this simply deletes the local database entry.
 * - For **MLS** conversations, this deletes the local conversation and also wipes it from the MLS client.
 */

@Mockable
interface DeleteConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

internal class DeleteConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository
) : DeleteConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocolInfo ->
            when (protocolInfo) {
                is Conversation.ProtocolInfo.MLSCapable -> {
                    conversationRepository.deleteConversationLocally(conversationId).flatMap {
                        mlsConversationRepository.leaveGroup(protocolInfo.groupId)
                    }
                }

                is Conversation.ProtocolInfo.Proteus -> {
                    conversationRepository.deleteConversationLocally(conversationId)
                }
            }
        }
    }
}
