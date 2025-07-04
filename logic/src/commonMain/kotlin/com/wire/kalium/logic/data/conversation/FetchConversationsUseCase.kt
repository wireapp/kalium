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
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.Mockable

/**
 * Use case responsible for fetching all available conversations from the backend,
 * handling pagination, and persisting them locally.
 *
 * Also handles partially failed or not found conversations by updating the local
 * state accordingly.
 */
@Mockable
internal interface FetchConversationsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

@OptIn(ConversationPersistenceApi::class)
internal class FetchConversationsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase
) : FetchConversationsUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> {
        var hasMore = true
        var lastPagingState: String? = null
        var latestResult: Either<CoreFailure, Unit> = Either.Right(Unit)
        while (hasMore && latestResult.isRight()) {
            latestResult = conversationRepository.fetchConversations(lastPagingState)
                .onSuccess { batch ->
                    val conversations = batch.response
                    if (conversations.conversationsFailed.isNotEmpty()) {
                        kaliumLogger.withFeatureId(CONVERSATIONS)
                            .d("Handling ${conversations.conversationsFailed.size} conversations failed")
                        conversationRepository.persistIncompleteConversations(conversations.conversationsFailed)
                    }
                    if (conversations.conversationsNotFound.isNotEmpty()) {
                        kaliumLogger.withFeatureId(CONVERSATIONS)
                            .d("Skipping ${conversations.conversationsNotFound.size} conversations not found")
                    }
                    persistConversations(
                        conversations = conversations.conversationsFound,
                        invalidateMembers = true
                    )
                    hasMore = batch.hasMore
                    lastPagingState = batch.lastPagingState

                }
                .map { }
        }

        return latestResult

    }
}
