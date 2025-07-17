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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.Mockable

/**
 * Use case responsible for fetching a single conversation from the backend
 * and persisting it locally if the fetch is successful.
 */
@Mockable
interface FetchConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

@OptIn(ConversationPersistenceApi::class)
internal class FetchConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val persistConversations: PersistConversationsUseCase
) : FetchConversationUseCase {

    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        return conversationRepository.fetchConversation(conversationId)
            .flatMap {
                persistConversations(
                    listOf(it),
                    invalidateMembers = true
                )
            }
    }
}
