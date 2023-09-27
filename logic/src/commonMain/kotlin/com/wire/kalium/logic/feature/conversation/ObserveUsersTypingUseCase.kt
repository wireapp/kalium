/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.conversation.TypingIndicatorRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Use case for observing current users typing in a given conversation.
 */
interface ObserveUsersTypingUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<Set<UserId>>
}

internal class ObserveUsersTypingUseCaseImpl(
    private val typingIndicatorRepository: TypingIndicatorRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveUsersTypingUseCase {
    override suspend operator fun invoke(conversationId: ConversationId): Flow<Set<UserId>> = withContext(dispatcher.default) {
        typingIndicatorRepository.observeUsersTyping(conversationId).map { usersEntries ->
            usersEntries.map { it.userId }.toSet()
        }
    }

}
