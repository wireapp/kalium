//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.models

import java.util.UUID

open class MessageBase {
    protected val messageId: UUID?
    protected val eventId: UUID?
    protected val userId: UUID?
    protected val clientId: String?
    protected val conversationId: UUID?
    protected val time: String?

    constructor(eventId: UUID?, msgId: UUID?, convId: UUID?, clientId: String?, userId: UUID?, time: String?) {
        this.eventId = eventId
        messageId = msgId
        conversationId = convId
        this.clientId = clientId
        this.userId = userId
        this.time = time
    }

    constructor(msg: MessageBase?) {
        eventId = msg.eventId
        messageId = msg.messageId
        conversationId = msg.conversationId
        clientId = msg.clientId
        userId = msg.userId
        time = msg.time
    }

    fun getConversationId(): UUID? {
        return conversationId
    }

    fun getUserId(): UUID? {
        return userId
    }

    fun getClientId(): String? {
        return clientId
    }

    fun getMessageId(): UUID? {
        return messageId
    }

    fun getTime(): String? {
        return time
    }

    fun getEventId(): UUID? {
        return eventId
    }
}
