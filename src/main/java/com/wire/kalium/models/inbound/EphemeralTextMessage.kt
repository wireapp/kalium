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
package com.wire.kalium.models.inbound

import java.util.UUID

class EphemeralTextMessage(
    val expireAfterMillis: Long,
    eventId: UUID,
    messageId: UUID,
    conversationId: UUID,
    clientId: String,
    userId: UUID,
    time: String,
    mentions: ArrayList<Mention>,
    text: String?

) : TextMessage(
    eventId = eventId,
    messageId = messageId,
    convId = conversationId,
    clientId = clientId,
    userId = userId,
    time = time
) {

    constructor(
        _expireAfterMillis: Long,
        _textMessage: TextMessage
    ) : this(
        expireAfterMillis = _expireAfterMillis,
        text = _textMessage.text,
        mentions = _textMessage.mentions,
        eventId = _textMessage.eventId,
        messageId = _textMessage.messageId,
        conversationId = _textMessage.conversationId,
        clientId = _textMessage.clientId,
        userId = _textMessage.userId,
        time = _textMessage.time
    )

    constructor(
        _expireAfterMillis: Long,
        _text: String,
        _mentions: ArrayList<Mention>,
        msg: MessageBase
    ) : this(
        expireAfterMillis = _expireAfterMillis,
        text = _text,
        mentions = _mentions,
        eventId = msg.eventId,
        messageId = msg.messageId,
        conversationId = msg.conversationId,
        clientId = msg.clientId,
        userId = msg.userId,
        time = msg.time
    )
}
