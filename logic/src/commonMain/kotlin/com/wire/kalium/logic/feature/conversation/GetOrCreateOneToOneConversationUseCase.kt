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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.flow.first

/**
 * Operation that creates one-to-one Conversation with specific [UserId] (only if it is absent in local DB)
 * and returns [Conversation] data.
 */
interface GetOrCreateOneToOneConversationUseCase {
    suspend operator fun invoke(otherUserId: UserId): CreateConversationResult
}

internal class GetOrCreateOneToOneConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val oneOnOneResolver: OneOnOneResolver
) : GetOrCreateOneToOneConversationUseCase {

    /**
     * The use case operation operation params and return type.
     *
     * @param otherUserId [UserId] private conversation with which we are interested in.
     * @return Result with [Conversation] in case of success, or [CoreFailure] if something went wrong:
     * can't get data from local DB, or can't create a conversation.
     */
    override suspend operator fun invoke(otherUserId: UserId): CreateConversationResult {
        // TODO periodically re-resolve one-on-one
        return conversationRepository.observeOneToOneConversationWithOtherUser(otherUserId)
            .first()
            .fold({ conversationFailure ->
                if (conversationFailure is StorageFailure.DataNotFound) {
                    resolveOneOnOneConversationWithUser(otherUserId)
                        .fold(
                            CreateConversationResult::Failure,
                            CreateConversationResult::Success
                        )
                } else {
                    CreateConversationResult.Failure(conversationFailure)
                }
            }, { conversation ->
                CreateConversationResult.Success(conversation)
            })
    }

    /**
     * Resolves one-on-one conversation with the user.
     * Resolving conversations is the process of:
     *
     * - Intersecting the supported protocols of the self user and the other user.
     * - Selecting the common protocol, based on the team settings with the highest priority.
     * - Get or create a conversation with the other user.
     * - If the protocol now is MLS, migrate the existing Proteus conversation to MLS.
     * - Mark the conversation as active.
     *
     * If no common protocol is found, and we have existing Proteus conversations, we do best effort to use them as fallback.
     */
    private suspend fun resolveOneOnOneConversationWithUser(otherUserId: UserId): Either<CoreFailure, Conversation> =
        userRepository.userById(otherUserId).flatMap { otherUser ->
            // TODO support lazily establishing mls group for team 1-1
            oneOnOneResolver.resolveOneOnOneConversationWithUser(
                user = otherUser,
                invalidateCurrentKnownProtocols = true
            )
        }.flatMap { conversationId -> conversationRepository.getConversationById(conversationId) }

}

sealed class CreateConversationResult {
    data class Success(val conversation: Conversation) : CreateConversationResult()
    data class Failure(val coreFailure: CoreFailure) : CreateConversationResult()
}
