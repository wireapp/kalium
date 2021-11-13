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
package com.wire.kalium.models.outbound

import com.waz.model.Messages
import com.waz.model.Messages.GenericMessage
import java.util.*

class Poll @JvmOverloads constructor(override val messageId: UUID = UUID.randomUUID()) : GenericMessageIdentifiable {
    private val poll: Messages.Composite.Builder = Messages.Composite.newBuilder()

    fun setExpectsReadConfirmation(value: Boolean): Poll? {
        poll.expectsReadConfirmation = value
        return this
    }

    fun addText(str: String?): Poll? {
        val text = Messages.Text.newBuilder()
            .setContent(str)
        val textItem = Messages.Composite.Item.newBuilder()
            .setText(text)
            .build()
        poll.addItems(textItem)
        return this
    }

    fun addText(msg: MessageText?): Poll? {
        msg?.let {
            val textItem = Messages.Composite.Item.newBuilder()
                .setText(it.builder)
                .build()
            poll.addItems(textItem)
        }
        return this
    }

    fun addButton(buttonId: String?, caption: String?): Poll? {
        val button = Messages.Button.newBuilder()
            .setText(caption)
            .setId(buttonId)
        val buttonItem = Messages.Composite.Item.newBuilder()
            .setButton(button)
        poll.addItems(buttonItem)
        return this
    }

    override fun createGenericMsg(): GenericMessage {
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setComposite(poll)
            .build()
    }

}
