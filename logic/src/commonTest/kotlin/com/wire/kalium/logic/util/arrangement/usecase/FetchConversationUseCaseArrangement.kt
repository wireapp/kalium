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
package com.wire.kalium.logic.util.arrangement.usecase

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

internal interface FetchConversationUseCaseArrangement {
    val fetchConversation: FetchConversationUseCase
    suspend fun withFetchConversationFailingWith(
        coreFailure: CoreFailure,
        transactionContext: CryptoTransactionContext = any(),
        conversationId: ConversationId = any(),
        reason: ConversationSyncReason = any(),
    ) {
        coEvery {
            fetchConversation(
                transactionContext = transactionContext,
                conversationId = conversationId,
                reason = reason
            )
        }.returns(Either.Left(coreFailure))
    }

    suspend fun withFetchConversationSucceeding(
        transactionContext: CryptoTransactionContext = any(),
        conversationId: ConversationId = any(),
        reason: ConversationSyncReason = any(),
    ) {
        coEvery {
            fetchConversation(
                transactionContext = transactionContext,
                conversationId = conversationId,
                reason = reason
            )
        }.returns(Either.Right(Unit))
    }

}

internal open class FetchConversationUseCaseArrangementImpl : FetchConversationUseCaseArrangement {
    override val fetchConversation: FetchConversationUseCase = mock(FetchConversationUseCase::class)
}
