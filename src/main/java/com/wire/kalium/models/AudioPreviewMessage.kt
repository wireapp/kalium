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
import com.waz.model.Messages.Asset.Original
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator

@JsonIgnoreProperties(ignoreUnknown = true)
class AudioPreviewMessage @JsonCreator constructor(
        @JsonProperty("duration") val duration: Long,
        @JsonProperty("levels") val levels: ByteArray,
        @JsonProperty("eventId") eventId: UUID,
        @JsonProperty("messageId") messageId: UUID,
        @JsonProperty("conversationId") convId: UUID,
        @JsonProperty("clientId") clientId: String,
        @JsonProperty("userId") userId: UUID,
        @JsonProperty("time") time: String,
        @JsonProperty("mimeType") mimeType: String,
        @JsonProperty("size") size: Long,
        @JsonProperty("name") name: String
) : OriginMessage(mimeType = mimeType, name = name, size = size, eventId = eventId, msgId = messageId, conversationId = convId, clientId = clientId, userId = userId, time = time) {

    constructor(msg: MessageBase, original: Original) :
            this(
                    duration = original.audio.durationInMillis,
                    levels = original.audio.normalizedLoudness.toByteArray(),
                    eventId = msg.eventId,
                    messageId = msg.messageId,
                    convId = msg.conversationId,
                    clientId = msg.clientId,
                    userId = msg.userId,
                    time = msg.time,
                    mimeType = original.mimeType,
                    size = original.size,
                    name = original.name
            )
}
