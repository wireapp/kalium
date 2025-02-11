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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.common.functional.Either

internal interface TypingIndicatorOutgoingRepository {
    suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit>

}

internal class TypingIndicatorOutgoingRepositoryImpl(
    private val typingIndicatorSenderHandler: TypingIndicatorSenderHandler,
    private val userPropertyRepository: UserPropertyRepository
) : TypingIndicatorOutgoingRepository {

    override suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit> {
        if (userPropertyRepository.getTypingIndicatorStatus()) {
            when (typingStatus) {
                Conversation.TypingIndicatorMode.STARTED ->
                    typingIndicatorSenderHandler.sendStartedAndEnqueueStoppingEvent(conversationId)

                Conversation.TypingIndicatorMode.STOPPED -> typingIndicatorSenderHandler.sendStoppingEvent(conversationId)
            }
        }
        return Either.Right(Unit)
    }

}
