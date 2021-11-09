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
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonCreator
import java.util.ArrayList

@JsonIgnoreProperties(ignoreUnknown = true)
class EditedTextMessage @JsonCreator constructor(
        @JsonProperty val replacingMessageId: UUID,
        @JsonProperty text: String,
        @JsonProperty quotedMessageId: UUID,
        @JsonProperty quotedMessageSha256: ByteArray,
        @JsonProperty mentions: ArrayList<Mention>,
        @JsonProperty("eventId") eventId: UUID,
        @JsonProperty("messageId") messageId: UUID,
        @JsonProperty("conversationId") convId: UUID,
        @JsonProperty("clientId") clientId: String,
        @JsonProperty("userId") userId: UUID,
        @JsonProperty("time") time: String
) : TextMessage(
        text = text,
        quotedMessageId = quotedMessageId,
        quotedMessageSha256 = quotedMessageSha256,
        mentions = mentions,
        eventId = eventId,
        messageId = messageId,
        convId = convId,
        clientId = clientId,
        userId = userId,
        time = time
) {

    constructor(
            _replacingMessageId: UUID,
            _text: String,
            _quotedMessageId: UUID,
            _quotedMessageSha256: ByteArray,
            _mentions: ArrayList<Mention>,
            msg: MessageBase
    ) : this(
            replacingMessageId = _replacingMessageId,
            text = _text,
            quotedMessageId = _quotedMessageId,
            quotedMessageSha256 = _quotedMessageSha256,
            mentions = _mentions,
            eventId = msg.eventId,
            messageId = msg.messageId,
            convId = msg.conversationId,
            clientId = msg.clientId,
            userId = msg.userId,
            time = msg.time
    )
}
