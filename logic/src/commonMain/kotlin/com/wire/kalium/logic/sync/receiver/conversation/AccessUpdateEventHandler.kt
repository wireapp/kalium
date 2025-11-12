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
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import io.mockative.Mockable

@Mockable
interface AccessUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.AccessUpdate): Either<StorageFailure, Unit>
}

@Suppress("FunctionNaming")
internal fun AccessUpdateEventHandler(
    selfUserId: UserId,
    conversationDAO: ConversationDAO,
    systemMessageInserter: SystemMessageInserter,
    conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) = object : AccessUpdateEventHandler {

    override suspend fun handle(event: Event.Conversation.AccessUpdate): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            val newAccessList = conversationMapper.fromModelToDAOAccess(event.access)
            val newAccessRole = conversationMapper.fromModelToDAOAccessRole(event.accessRole)

            val oldAccessRole = conversationDAO.getConversationById(event.conversationId.toDao())?.accessRole
            val hadServiceRole = oldAccessRole?.contains(ConversationEntity.AccessRole.SERVICE) == true
            val hasServiceRoleNow = newAccessRole.contains(ConversationEntity.AccessRole.SERVICE)

            conversationDAO.updateAccess(
                conversationID = event.conversationId.toDao(),
                accessList = newAccessList,
                accessRoleList = newAccessRole
            )

            // Persist system message if apps access changed
            persistConversationAppsAccessChangedMessageIfChanged(event, hadServiceRole, hasServiceRoleNow)
        }

    private suspend fun persistConversationAppsAccessChangedMessageIfChanged(
        event: Event.Conversation.AccessUpdate,
        hadServiceRole: Boolean,
        hasServiceRoleNow: Boolean
    ) {
        when {
            hadServiceRole && !hasServiceRoleNow -> {
                // Apps access was disabled
                systemMessageInserter.insertConversationAppsAccessChanged(
                    eventId = event.id,
                    conversationId = event.conversationId,
                    isAppsAccessEnabled = false
                )
            }

            !hadServiceRole && hasServiceRoleNow -> {
                // Apps access was enabled
                systemMessageInserter.insertConversationAppsAccessChanged(
                    eventId = event.id,
                    conversationId = event.conversationId,
                    isAppsAccessEnabled = true
                )
            }
        }
    }
}
