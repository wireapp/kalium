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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap

internal interface SyncConversationsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

/**
 * This use case will sync against the backend the conversations of the current user.
 */
internal class SyncConversationsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val systemMessageInserter: SystemMessageInserter
) : SyncConversationsUseCase {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> =
        conversationRepository.getConversationIds(Conversation.Type.Group.Regular, Conversation.Protocol.PROTEUS)
            .flatMap { proteusConversationIds ->
                conversationRepository.fetchConversations()
                    .flatMap {
                        reportConversationsWithPotentialHistoryLoss(proteusConversationIds)
                    }
            }

    private suspend fun reportConversationsWithPotentialHistoryLoss(
        proteusConversationIds: List<ConversationId>
    ): Either<StorageFailure, Unit> =
        conversationRepository.getConversationIds(Conversation.Type.Group.Regular, Conversation.Protocol.MLS)
            .flatMap { mlsConversationIds ->
                val conversationsWithUpgradedProtocol = mlsConversationIds.intersect(proteusConversationIds)
                for (conversationId in conversationsWithUpgradedProtocol) {
                    systemMessageInserter.insertHistoryLostProtocolChangedSystemMessage(conversationId)
                }
                Either.Right(Unit)
            }
}
