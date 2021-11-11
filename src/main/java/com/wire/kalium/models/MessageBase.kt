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

import com.wire.kalium.tools.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
open class MessageBase(
        @Serializable(with = UUIDSerializer::class) open val messageId: UUID,
        @Serializable(with = UUIDSerializer::class)  open val eventId: UUID,
        @Serializable(with = UUIDSerializer::class)  open val userId: UUID,
        open val clientId: String,
        @Serializable(with = UUIDSerializer::class) open val conversationId: UUID,
        open val time: String
) {
    constructor(msg: MessageBase) : this(
            eventId = msg.eventId,
            messageId = msg.messageId,
            conversationId = msg.conversationId,
            clientId = msg.clientId,
            userId = msg.userId,
            time = msg.time
    )
}
