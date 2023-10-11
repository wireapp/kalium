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

import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Use case for clearing and drop orphaned typing indicators
 */
interface ClearUsersTypingEventsUseCase {
    suspend operator fun invoke()
}

internal class ClearUsersTypingEventsUseCaseImpl(
    private val typingIndicatorIncomingRepository: TypingIndicatorIncomingRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ClearUsersTypingEventsUseCase {
    override suspend operator fun invoke() {
        withContext(dispatcher.io) {
            typingIndicatorIncomingRepository.clearExpiredTypingIndicators()
        }
    }
}
