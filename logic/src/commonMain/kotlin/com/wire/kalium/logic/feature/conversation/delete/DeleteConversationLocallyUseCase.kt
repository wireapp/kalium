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
import com.wire.kalium.common.functional.left
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.ClearConversationContentUseCase

interface DeleteConversationLocallyUseCase {
    /**
     * Delete local conversation which:
     * - Clear all local assets
     * - Clear content
     * - Remove conversation
     * - Send Signal message to other clients to do the same
     *
     * @param conversationId - id of conversation to delete
     */
    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

internal class DeleteConversationLocallyUseCaseImpl(
    private val clearConversationContent: ClearConversationContentUseCase,
    private val deleteConversation: DeleteConversationUseCase,
    private val transactionProvider: CryptoTransactionProvider
) : DeleteConversationLocallyUseCase {

    override suspend fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        val clearResult = clearConversationContent(conversationId, true)
        return if (clearResult is ClearConversationContentUseCase.Result.Failure) {
            clearResult.failure.left()
        } else {
            transactionProvider.transaction("DeleteConversationLocally") { deleteConversation(it, conversationId) }
        }
    }
}
