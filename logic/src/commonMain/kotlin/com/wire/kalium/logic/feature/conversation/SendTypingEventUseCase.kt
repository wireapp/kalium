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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.TypingIndicatorOutgoingRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * UseCase for sending a typing event to a specific [ConversationId]
 *
 * Underlying implementation will take care of enqueuing the event for a [Conversation.TypingIndicatorMode.STOPPED]
 * after a certain amount of time.
 *
 */
interface SendTypingEventUseCase {
    suspend operator fun invoke(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    )
}

internal class SendTypingEventUseCaseImpl(
    private val typingIndicatorRepository: TypingIndicatorOutgoingRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : SendTypingEventUseCase {
    override suspend fun invoke(conversationId: ConversationId, typingStatus: Conversation.TypingIndicatorMode) {
        withContext(dispatcher.io) {
            typingIndicatorRepository.sendTypingIndicatorStatus(conversationId, typingStatus)
        }
    }
}
