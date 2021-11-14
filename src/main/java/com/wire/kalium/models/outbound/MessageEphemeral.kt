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
package com.wire.kalium.models.outbound

import com.waz.model.Messages
import com.waz.model.Messages.Ephemeral
import com.waz.model.Messages.GenericMessage
import java.util.*

class MessageEphemeral(mills: Long) : GenericMessageIdentifiable {
    private val builder = Ephemeral.newBuilder()
    override val messageId: UUID = UUID.randomUUID()

    fun setText(text: String?): MessageEphemeral? {
        val textBuilder = Messages.Text.newBuilder()
            .setContent(text)
        builder.setText(textBuilder)
        return this
    }

    override fun createGenericMsg(): GenericMessage {
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setEphemeral(builder)
            .build()
    }

    init {
        builder.expireAfterMillis = mills
    }
}
