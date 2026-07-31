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

package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId

/**
 * Use case for ensuring that the meeting's MLS conversation group is established.
 * If the conversation related to the meeting is MLS-capable then tries to establish or join MLS group using dedicated use case.
 * If the MLS group is already established or if the conversation is not MLS-capable, it does nothing and returns success.
 */
public interface EnsureMeetingIsMLSEstablishedUseCase {
    public suspend operator fun invoke(conversationId: ConversationId): Boolean
}

internal class EnsureMeetingIsMLSEstablishedUseCaseImpl(
    private val transactionProvider: CryptoTransactionProvider,
    private val conversationRepository: ConversationRepository,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
) : EnsureMeetingIsMLSEstablishedUseCase {
    override suspend operator fun invoke(conversationId: ConversationId) =
        conversationRepository.getNonDeletedConversationById(conversationId)
            .flatMap { conversation ->
                (conversation.protocol as? Conversation.ProtocolInfo.MLSCapable)?.let { mlsProtocolInfo ->
                    if (mlsProtocolInfo.groupState == Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED) {
                        Either.Right(Unit) // MLS group is already established, return success
                    } else {
                        transactionProvider.transaction("ensureMeetingIsMLSEstablished") { transactionContext ->
                            joinExistingMLSConversation(transactionContext, conversationId)
                        }
                    }
                } ?: Either.Right(Unit) // Conversation is not MLS-capable, return success
            }
            .isRight()
}
