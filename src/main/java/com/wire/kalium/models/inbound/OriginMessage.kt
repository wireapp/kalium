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

import java.util.*

abstract class OriginMessage(
        val mimeType: String,
        val name: String,
        val size: Long,
        eventId: UUID,
        msgId: UUID,
        conversationId: UUID,
        clientId: String,
        userId: UUID,
        time: String
) : MessageBase(eventId, msgId, conversationId, clientId, userId, time) {
    constructor(_mimeType: String, _name: String, _size: Long, msg: MessageBase) : this(
            mimeType = _mimeType,
            name = _name,
            size = _size,
            eventId = msg.eventId,
            msgId = msg.messageId,
            conversationId = msg.conversationId,
            clientId = msg.clientId,
            userId = msg.userId,
            time = msg.time
    )
}
