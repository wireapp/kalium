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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Refresh conversations without metadata, only if necessary.
 */
public interface RefreshConversationsWithoutMetadataUseCase {
    public suspend operator fun invoke()
}

internal class RefreshConversationsWithoutMetadataUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : RefreshConversationsWithoutMetadataUseCase {
    override suspend fun invoke() = withContext(dispatchers.io) {
        conversationRepository.getConversationIdsWithoutMetadata().fold(
            { kaliumLogger.w("Failed to load conversations without metadata") },
            { conversationIds ->
                conversationIds.distinct().chunked(CONVERSATIONS_PER_BATCH).forEach { batch ->
                    conversationRepository.fetchConversationListDetails(batch).flatMap { response ->
                        // Only authoritative metadata is persisted. Not-found and failed IDs keep
                        // their restored history and remain eligible for a later metadata sync.
                        if (response.conversationsFound.isEmpty()) {
                            Either.Right(Unit)
                        } else {
                            transactionProvider.transaction("RefreshConversationsWithoutMetadata") { transactionContext ->
                                persistConversations(transactionContext, response.conversationsFound, false)
                            }
                        }
                    }.onFailure {
                        kaliumLogger.w("Failed to refresh a conversation metadata batch; metadata remains retryable")
                    }
                }
            }
        )
    }

    private companion object {
        const val CONVERSATIONS_PER_BATCH = 100
    }
}
