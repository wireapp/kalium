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

import com.waz.model.Messages
import java.util.UUID

//@JsonIgnoreProperties(ignoreUnknown = true)
//@JsonInclude(JsonInclude.Include.NON_NULL)
open class TextMessage constructor(
//        @JsonProperty("eventId")
    eventId: UUID,
//        @JsonProperty("messageId")
    messageId: UUID,
//        @JsonProperty("conversationId")
    convId: UUID,
//        @JsonProperty("clientId")
    clientId: String,
//        @JsonProperty("userId")
    userId: UUID,
//        @JsonProperty("time")
    time: String
) : MessageBase(eventId = eventId, messageId = messageId, conversationId = convId, clientId = clientId, userId = userId, time = time) {

    var text: String? = null
    var quotedMessageId: UUID? = null
    var quotedMessageSha256: ByteArray? = null
    var mentions: ArrayList<Mention> = arrayListOf()

    constructor(_text: String, _mentions: ArrayList<Mention>, msgBase: MessageBase) :
            this(
                eventId = msgBase.eventId,
                messageId = msgBase.messageId,
                convId = msgBase.conversationId,
                clientId = msgBase.clientId,
                userId = msgBase.userId,
                time = msgBase.time
            )

    constructor(_messageText: Messages.Text, _messageBase: MessageBase) : this(
        eventId = _messageBase.eventId,
        messageId = _messageBase.messageId,
        convId = _messageBase.conversationId,
        clientId = _messageBase.clientId,
        userId = _messageBase.userId,
        time = _messageBase.time
    ) {

        text = if (_messageText.hasContent()) _messageText.content else null

        if (_messageText.hasQuote()) {
            quotedMessageId = UUID.fromString(_messageText.quote.quotedMessageId)
            quotedMessageSha256 = _messageText.quote.quotedMessageSha256.toByteArray()
        }

        for (mention in _messageText.mentionsList) {
            addMention(mention.userId, mention.start, mention.length)
        }
    }

    fun addMention(userId: String?, offset: Int, len: Int) {
        val mention = Mention(UUID.fromString(userId), offset, len)
        mentions.add(mention)
    }

    data class Mention(
        val userId: UUID,
        val offset: Int,
        val length: Int
    )
}
