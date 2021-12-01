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

import com.waz.model.Messages.Asset.RemoteData
import java.util.UUID

class RemoteMessage(
    val assetId: String,
    val assetToken: String,
    val otrKey: ByteArray,
    val sha256: ByteArray,
    eventId: UUID,
    messageId: UUID,
    convId: UUID,
    clientId: String,
    userId: UUID,
    time: String
) : MessageBase(eventId = eventId, messageId = messageId, conversationId = convId, clientId = clientId, userId = userId, time = time) {

    constructor(msg: MessageBase, uploaded: RemoteData) : this(
        assetId = uploaded.assetId,
        assetToken = uploaded.assetToken,
        otrKey = uploaded.otrKey.toByteArray(),
        sha256 = uploaded.sha256.toByteArray(),
        eventId = msg.eventId,
        messageId = msg.messageId,
        convId = msg.conversationId,
        clientId = msg.clientId,
        userId = msg.userId,
        time = msg.time
    )
}
