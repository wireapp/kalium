//
// Wire
// Copyright (C) 2021 Wire Swiss GmbH
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
package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.google.protobuf.ByteString
import com.waz.model.Messages.Quote
import com.waz.model.Messages

class MessageText(text: String?) : GenericMessageIdentifiable {
    val builder = Messages.Text.newBuilder()
    override val messageId: UUID = UUID.randomUUID()
    fun setExpectsReadConfirmation(value: Boolean): MessageText? {
        builder.expectsReadConfirmation = value
        return this
    }

    fun setText(text: String?): MessageText? {
        builder.content = text
        return this
    }

    fun setQuote(msgId: UUID?, sha256: ByteArray?): MessageText? {
        val quote = Quote.newBuilder()
            .setQuotedMessageId(msgId.toString())
            .setQuotedMessageSha256(ByteString.copyFrom(sha256))
        builder.setQuote(quote)
        return this
    }

    fun addMention(mentionUser: UUID?, offset: Int, len: Int): MessageText? {
        val mention = Messages.Mention.newBuilder()
            .setUserId(mentionUser.toString())
            .setLength(len)
            .setStart(offset)
        builder.addMentions(mention)
        return this
    }

    override fun createGenericMsg(): GenericMessage? {
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setText(builder)
            .build()
    }

    init {
        setText(text)
    }
}
