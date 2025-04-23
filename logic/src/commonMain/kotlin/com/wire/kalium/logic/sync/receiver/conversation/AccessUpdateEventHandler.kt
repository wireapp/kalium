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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import io.mockative.Mockable

@Mockable
interface AccessUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.AccessUpdate): Either<StorageFailure, Unit>
}

@Suppress("FunctionNaming")
fun AccessUpdateEventHandler(
    selfUserId: UserId,
    conversationDAO: ConversationDAO,
    conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) = object : AccessUpdateEventHandler {

    override suspend fun handle(event: Event.Conversation.AccessUpdate): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateAccess(
                conversationID = event.conversationId.toDao(),
                accessList = conversationMapper.fromModelToDAOAccess(event.access),
                accessRoleList = conversationMapper.fromModelToDAOAccessRole(event.accessRole)
            )
        }
}
