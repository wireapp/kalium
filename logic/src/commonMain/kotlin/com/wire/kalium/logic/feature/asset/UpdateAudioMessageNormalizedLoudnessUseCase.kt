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
package com.wire.kalium.logic.feature.asset

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

interface UpdateAudioMessageNormalizedLoudnessUseCase {
    /**
     * Updates the audio waves mask for a given message in a conversation.
     * @param conversationId The ID of the conversation containing the message.
     * @param messageId The ID of the message to update.
     * @param normalizedLoudness The new normalized loudness data to set.
     * @return Either a CoreFailure on failure or Unit on success.
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        normalizedLoudness: ByteArray
    ): Either<CoreFailure, Unit>
}

internal class UpdateAudioMessageNormalizedLoudnessUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl.instance
) : UpdateAudioMessageNormalizedLoudnessUseCase {
    override suspend fun invoke(
        conversationId: ConversationId,
        messageId: String,
        normalizedLoudness: ByteArray
    ): Either<CoreFailure, Unit> = withContext(dispatcher.io) {
        messageRepository.updateAudioMessageNormalizedLoudness(
            conversationId = conversationId,
            messageId = messageId,
            normalizedLoudness = normalizedLoudness
        )
    }
}
