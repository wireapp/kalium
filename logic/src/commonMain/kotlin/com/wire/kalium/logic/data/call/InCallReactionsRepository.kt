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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.onSuccess
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal interface InCallReactionsRepository {
    suspend fun addInCallReaction(emojis: Set<String>, senderUserId: UserId)
    fun observeInCallReactions(): Flow<InCallReactionMessage>
}

internal class InCallReactionsDataSource(
    private val userRepository: UserRepository,
) : InCallReactionsRepository {

    private val inCallReactionsFlow: MutableSharedFlow<InCallReactionMessage> =
        MutableSharedFlow(extraBufferCapacity = BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun addInCallReaction(emojis: Set<String>, senderUserId: UserId) {
        userRepository.userById(senderUserId).onSuccess { user ->
            inCallReactionsFlow.emit(
                InCallReactionMessage(emojis, senderUserId, user.name)
            )
        }
    }

    override fun observeInCallReactions(): Flow<InCallReactionMessage> = inCallReactionsFlow.asSharedFlow()

    private companion object {
        const val BUFFER_SIZE = 32 // drop after this threshold
    }
}
