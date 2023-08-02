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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map

internal interface JoinOrEstablishMLSOneToOneUseCase {
    suspend operator fun invoke(userId: UserId): Either<CoreFailure, ConversationId>
}

internal class JoinOrEstablishMLSOneToOneUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val connectionRepository: ConnectionRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
) : JoinOrEstablishMLSOneToOneUseCase {

    override suspend fun invoke(userId: UserId): Either<CoreFailure, ConversationId> =
        // TODO Get current conversation with other user
        conversationRepository.getConversationIds(Conversation.Type.ONE_ON_ONE, Conversation.Protocol.MLS)
            .flatMap { conversationIds ->
                if (conversationIds.isNotEmpty()) {
                    return@flatMap Either.Right(conversationIds.first())
                }
                conversationRepository.fetchMlsOneToOneConversation(userId).flatMap { conversation ->
                    joinExistingMLSConversationUseCase(conversation.id).map { conversation.id }
                }
            }

}
