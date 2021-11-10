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
import com.waz.model.Messages

class MessageDelete(private val delMessageId: UUID?) : GenericMessageIdentifiable {
    override val messageId: UUID = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage {
        val del = Messages.MessageDelete.newBuilder()
            .setMessageId(delMessageId.toString())
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setDeleted(del)
            .build()
    }
}
