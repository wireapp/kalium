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
package com.wire.kalium.logic.data.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.util.DateTimeUtil

interface SystemMessageBuilder {
    suspend fun insertProtocolChangedSystemMessage(event: Event.Conversation.ConversationProtocol)
}

class SystemMessageBuilderImpl(
    private val persistMessage: PersistMessageUseCase
): SystemMessageBuilder {
    override suspend fun insertProtocolChangedSystemMessage(event: Event.Conversation.ConversationProtocol) {
        val message = Message.System(
            uuid4().toString(),
            MessageContent.ConversationProtocolChanged(
                protocol = event.protocol
            ),
            event.conversationId,
            DateTimeUtil.currentIsoDateTimeString(),
            event.senderUserId,
            Message.Status.SENT,
            Message.Visibility.VISIBLE
        )

        persistMessage(message)
    }
}
