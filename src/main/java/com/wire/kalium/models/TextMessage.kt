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
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.ArrayList

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
open class TextMessage : MessageBase {
    @JsonProperty
    private var text: String? = null

    @JsonProperty
    private var quotedMessageId: UUID? = null

    @JsonProperty
    private var quotedMessageSha256: ByteArray?

    @JsonProperty
    private val mentions: ArrayList<Mention?>? = ArrayList()

    @JsonCreator
    constructor(
        @JsonProperty("eventId") eventId: UUID?,
        @JsonProperty("messageId") messageId: UUID?,
        @JsonProperty("conversationId") convId: UUID?,
        @JsonProperty("clientId") clientId: String?,
        @JsonProperty("userId") userId: UUID?,
        @JsonProperty("time") time: String?
    ) : super(eventId, messageId, convId, clientId, userId, time) {
    }

    constructor(msg: MessageBase?) : super(msg) {}

    fun getText(): String? {
        return text
    }

    fun setText(text: String?) {
        this.text = text
    }

    fun getQuotedMessageId(): UUID? {
        return quotedMessageId
    }

    fun setQuotedMessageId(quotedMessageId: UUID?) {
        this.quotedMessageId = quotedMessageId
    }

    fun getQuotedMessageSha256(): ByteArray? {
        return quotedMessageSha256
    }

    fun setQuotedMessageSha256(quotedMessageSha256: ByteArray?) {
        this.quotedMessageSha256 = quotedMessageSha256
    }

    fun addMention(userId: String?, offset: Int, len: Int) {
        val mention = Mention()
        mention.userId = UUID.fromString(userId)
        mention.offset = offset
        mention.length = len
        mentions.add(mention)
    }

    fun getMentions(): ArrayList<Mention?>? {
        return mentions
    }

    class Mention {
        var userId: UUID? = null
        var offset = 0
        var length = 0
    }
}
