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
import dev.mokkery.everySuspend
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.matcher.matches
import dev.mokkery.mock

internal interface FetchConversationUseCaseArrangement {
    val fetchConversation: FetchConversationUseCase
    suspend fun withFetchConversationFailingWith(
        coreFailure: CoreFailure,
        transactionContext: (CryptoTransactionContext) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true },
        reason: (ConversationSyncReason) -> Boolean = { true },
    ) {
        everySuspend {
            fetchConversation(
                transactionContext = matches { transactionContext(it) },
                conversationId = matches { conversationId(it) },
                reason = matches { reason(it) }
            )
        }.returns(Either.Left(coreFailure))
    }

    suspend fun withFetchConversationSucceeding(
        transactionContext: (CryptoTransactionContext) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true },
        reason: (ConversationSyncReason) -> Boolean = { true },
    ) {
        everySuspend {
            fetchConversation(
                transactionContext = matches { transactionContext(it) },
                conversationId = matches { conversationId(it) },
                reason = matches { reason(it) }
            )
        }.returns(Either.Right(Unit))
    }

}

internal open class FetchConversationUseCaseArrangementImpl : FetchConversationUseCaseArrangement {
    override val fetchConversation: FetchConversationUseCase = mock<FetchConversationUseCase>(mode = MockMode.autoUnit)
}
