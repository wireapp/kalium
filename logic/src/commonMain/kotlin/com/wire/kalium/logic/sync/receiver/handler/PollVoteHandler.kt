/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PollMessageRepository

internal interface PollVoteHandler {
    suspend fun handle(
        message: Message.Signaling,
        content: MessageContent.PollVote
    )
}

internal class PollVoteHandlerImpl internal constructor(
    private val pollMessageRepository: PollMessageRepository
) : PollVoteHandler {
    override suspend fun handle(
        message: Message.Signaling,
        content: MessageContent.PollVote
    ) {
        pollMessageRepository.recordVote(
            conversationId = message.conversationId,
            pollMessageId = content.referencedMessageId,
            voterId = message.senderUserId,
            selectedOptionIds = content.selectedOptionIds,
            date = message.date
        )
    }
}
