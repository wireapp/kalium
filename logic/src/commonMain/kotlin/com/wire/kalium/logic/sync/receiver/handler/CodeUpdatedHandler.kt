/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.conversation.link
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO

internal interface CodeUpdatedHandler {
    suspend fun handle(event: Event.Conversation.CodeUpdated): Either<StorageFailure, Unit>
}

internal class CodeUpdateHandlerImpl internal constructor(
    private val conversationDAO: ConversationDAO,
    private val serverConfigLinks: ServerConfig.Links
) : CodeUpdatedHandler {
    override suspend fun handle(event: Event.Conversation.CodeUpdated) = wrapStorageRequest {
        conversationDAO.updateGuestRoomLink(
            event.conversationId.toDao(), event.link(serverConfigLinks.accounts), event.isPasswordProtected
        )
    }
}
