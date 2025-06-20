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
package com.wire.kalium.logic.util.arrangement.usecase

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.mock

internal interface DeleteConversationArrangement {
    val deleteConversation: DeleteConversationUseCase

    suspend fun withDeletingConversationSucceeding(conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()))
    suspend fun withDeletingConversationFailing(conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()))
}

internal open class DeleteConversationArrangementImpl : DeleteConversationArrangement {

    override val deleteConversation: DeleteConversationUseCase = mock(DeleteConversationUseCase::class)

    override suspend fun withDeletingConversationSucceeding(conversationId: Matcher<ConversationId>) {
        coEvery {
            deleteConversation(io.mockative.matches { conversationId.matches(it) })
        }.returns(Either.Right(Unit))
    }

    override suspend fun withDeletingConversationFailing(conversationId: Matcher<ConversationId>) {
        coEvery {
            deleteConversation(io.mockative.matches { conversationId.matches(it) })
        }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
    }

}
