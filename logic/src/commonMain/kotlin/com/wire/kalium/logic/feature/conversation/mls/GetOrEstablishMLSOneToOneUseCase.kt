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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map

/**
 * Use case that will return an existing MLS-capable
 * one-on-one conversation or establish a new one.
 * In case the conversation already exists, but it's not initialized yet
 * (epoch == 0), it will attempt to join it, returning failure if it fails.
 */
internal interface GetOrEstablishMLSOneToOneUseCase {
    /**
     * Attempts to find an existing MLS-capable one-on-one conversation,
     * or creates a new one if none is found.
     * In case the conversation already exists, but it's not initialized yet
     * (epoch == 0), it will attempt to join it, returning failure if it fails.
     * @param userId The user ID of the other participant.
     */
    suspend operator fun invoke(userId: UserId): Either<CoreFailure, ConversationId>
}

internal class GetOrEstablishMLSOneToOneUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val joinExistingMLSConversationUseCase: JoinExistingMLSConversationUseCase,
) : GetOrEstablishMLSOneToOneUseCase {

    override suspend fun invoke(userId: UserId): Either<CoreFailure, ConversationId> =
        conversationRepository.getConversationsByUserId(userId).flatMap { conversations ->
            // Look for an existing MLS-capable conversation one-on-one
            val initializedMLSOneOnOne = conversations.firstOrNull {
                val isOneOnOne = it.type == Conversation.Type.ONE_ON_ONE
                val protocol = it.protocol
                val isMLSInitialized = protocol is Conversation.ProtocolInfo.MLSCapable &&
                        protocol.epoch != 0UL
                isOneOnOne && isMLSInitialized
            }

            if (initializedMLSOneOnOne != null) {
                Either.Right(initializedMLSOneOnOne.id)
            } else {
                conversationRepository.fetchMlsOneToOneConversation(userId).flatMap { conversation ->
                    joinExistingMLSConversationUseCase(conversation.id).map { conversation.id }
                }
            }
        }
}
