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
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.di.UserSessionBinding
import dev.zacsweers.metro.Inject

/**
 * Use case responsible for ensuring that a conversation is available locally.
 *
 * If the conversation with the given ID is not available in the local database,
 * it will be fetched from the backend using [FetchConversationUseCase].
 */
internal interface FetchConversationIfUnknownUseCase {
    suspend operator fun invoke(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        reason: ConversationSyncReason = ConversationSyncReason.Other,
    ): Either<CoreFailure, Unit>
}

@Inject
internal class FetchConversationIfUnknownUseCaseImpl(
    @UserSessionBinding("conversationRepository")
    private val conversationRepository: ConversationRepository,
    @UserSessionBinding("fetchConversationUseCase")
    private val fetchConversation: FetchConversationUseCase
) : FetchConversationIfUnknownUseCase {

    override suspend fun invoke(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        reason: ConversationSyncReason,
    ): Either<CoreFailure, Unit> {
        return conversationRepository.getConversationById(conversationId)
            .run {
                if (isLeft()) {
                    fetchConversation(transactionContext, conversationId, reason)
                } else {
                    Either.Right(Unit)
                }
            }
    }
}
