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

import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.UserSummary
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Use case for observing current users typing in a given conversation.
 * This will get their info details from the local database.
 */
interface ObserveUsersTypingUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Flow<Set<UserSummary>>
}

internal class ObserveUsersTypingUseCaseImpl(
    private val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepository,
    private val userRepository: UserRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveUsersTypingUseCase {
    override suspend operator fun invoke(conversationId: ConversationId): Flow<Set<UserSummary>> = withContext(dispatcher.io) {
        typingIndicatorIncomingRepository.observeUsersTyping(conversationId).map { usersEntries ->
            userRepository.getUsersSummaryByIds(usersEntries.map { it }).fold({
                kaliumLogger.w("Users not found locally, skipping... $it")
                emptySet()
            }, { it.toSet() })
        }
    }

}
