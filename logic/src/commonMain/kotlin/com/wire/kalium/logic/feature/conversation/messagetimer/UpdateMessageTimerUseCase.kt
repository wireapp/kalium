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
package com.wire.kalium.logic.feature.conversation.messagetimer

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.fold

/**
 * A use case used to update messages self deletion for conversation
 */
interface UpdateMessageTimerUseCase {
    suspend operator fun invoke(conversationId: ConversationId, messageTimer: Long?): Result

    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

class UpdateMessageTimerUseCaseImpl internal constructor(
    private val conversationGroupRepository: ConversationGroupRepository,
) : UpdateMessageTimerUseCase {
    override suspend fun invoke(conversationId: ConversationId, messageTimer: Long?): UpdateMessageTimerUseCase.Result =
        conversationGroupRepository.updateMessageTimer(conversationId, messageTimer)
            .fold(
                { UpdateMessageTimerUseCase.Result.Failure(it) },
                { UpdateMessageTimerUseCase.Result.Success }
            )
}
