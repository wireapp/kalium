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
package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter

@Mockable
internal interface InCallReactionsRepository {
    suspend fun addInCallReaction(conversationId: ConversationId, senderUserId: UserId, emojis: Set<String>)
    fun observeInCallReactions(conversationId: ConversationId): Flow<InCallReactionMessage>
}

internal class InCallReactionsDataSource : InCallReactionsRepository {

    private val inCallReactionsFlow: MutableSharedFlow<InCallReactionMessage> =
        MutableSharedFlow(extraBufferCapacity = BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun addInCallReaction(conversationId: ConversationId, senderUserId: UserId, emojis: Set<String>) {
        inCallReactionsFlow.emit(InCallReactionMessage(conversationId, senderUserId, emojis))
    }

    override fun observeInCallReactions(conversationId: ConversationId): Flow<InCallReactionMessage> = inCallReactionsFlow.asSharedFlow()
        .filter { it.conversationId == conversationId }

    private companion object {
        const val BUFFER_SIZE = 32 // drop after this threshold
    }
}
