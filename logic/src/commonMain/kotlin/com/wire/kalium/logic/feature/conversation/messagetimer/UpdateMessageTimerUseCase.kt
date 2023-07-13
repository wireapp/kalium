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
package com.wire.kalium.logic.feature.conversation.messagetimer

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.util.DateTimeUtil

/**
 * A use case used to update messages self deletion for conversation
 */
interface UpdateMessageTimerUseCase {
    suspend operator fun invoke(conversationId: ConversationId, messageTimer: Long?): Result

    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

class UpdateMessageTimerUseCaseImpl internal constructor(
    private val conversationGroupRepository: ConversationGroupRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
) : UpdateMessageTimerUseCase {
    override suspend fun invoke(conversationId: ConversationId, messageTimer: Long?): UpdateMessageTimerUseCase.Result =
        conversationGroupRepository.updateMessageTimer(conversationId, messageTimer)
            .onSuccess {
                val message = Message.System(
                    uuid4().toString(),
                    MessageContent.ConversationMessageTimerChanged(
                        messageTimer = messageTimer
                    ),
                    conversationId,
                    DateTimeUtil.currentIsoDateTimeString(),
                    selfUserId,
                    Message.Status.SENT,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )
                persistMessage(message)
            }
            .fold(
                { UpdateMessageTimerUseCase.Result.Failure(it) },
                { UpdateMessageTimerUseCase.Result.Success }
            )
}
