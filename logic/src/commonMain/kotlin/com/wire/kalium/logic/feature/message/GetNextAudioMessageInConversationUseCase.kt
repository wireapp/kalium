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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Provides a way to get a messageId of next AudioMessage after [messageId] in [ConversationId] conversation.
 */
class GetNextAudioMessageInConversationUseCase internal constructor(
    private val messageRepository: MessageRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String
    ): Result = withContext(dispatchers.io) {
        messageRepository.getNextAudioMessageInConversation(conversationId, messageId)
            .fold({ Result.Failure(it) }, { Result.Success(it) })
    }

    sealed interface Result {

        data class Success(val messageId: String) : Result

        /**
         * [StorageFailure.DataNotFound] in case there is no AudioMessage or some other generic error.
         */
        data class Failure(val cause: CoreFailure) : Result
    }
}
