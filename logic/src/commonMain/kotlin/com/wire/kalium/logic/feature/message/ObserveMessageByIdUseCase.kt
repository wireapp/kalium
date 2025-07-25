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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * This use case will observe and return the [Flow] of [Message] for a specific [ConversationId] and messageId.
 */
class ObserveMessageByIdUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(conversationId: ConversationId, messageId: String): Flow<Result> =
        messageRepository.observeMessageById(conversationId, messageId)
            .map { it.fold({ Result.Failure(it) }, { Result.Success(it) }) }
            .flowOn(dispatchers.io)

    sealed interface Result {

        data class Success(val message: Message) : Result

        /**
         * [StorageFailure.DataNotFound] or some other generic error.
         */
        data class Failure(val cause: CoreFailure) : Result
    }
}
