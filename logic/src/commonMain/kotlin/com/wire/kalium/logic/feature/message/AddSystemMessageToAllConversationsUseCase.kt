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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.kaliumLogger

import com.wire.kalium.util.DateTimeUtil

/**
 * persist a local system message to all conversations
 */
interface AddSystemMessageToAllConversationsUseCase {
    suspend operator fun invoke()
}

class AddSystemMessageToAllConversationsUseCaseImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val selfUserId: UserId
) : AddSystemMessageToAllConversationsUseCase {
    override suspend operator fun invoke() {
        kaliumLogger.w("persist HistoryLost system message after recovery for all conversations")
        val generatedMessageUuid = uuid4().toString()
        val message = Message.System(
            id = generatedMessageUuid,
            content = MessageContent.HistoryLost,
            // the conversation id will be ignored in the repo level!
            conversationId = ConversationId("", ""),
            date = DateTimeUtil.currentIsoDateTimeString(),
            senderUserId = selfUserId,
            status = Message.Status.Sent,
            expirationData = null
        )
        messageRepository.persistSystemMessageToAllConversations(message)
    }
}
