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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.SystemMessageInserter

public fun interface AccessUpdateEventHandler {
    public suspend fun handle(event: Event.Conversation.AccessUpdate): Either<StorageFailure, Unit>
}

public class AccessUpdateEventHandlerImpl public constructor(
    private val conversationEventRepository: ConversationEventRepository,
    private val systemMessageInserter: SystemMessageInserter,
) : AccessUpdateEventHandler {

    override suspend fun handle(event: Event.Conversation.AccessUpdate): Either<StorageFailure, Unit> =
        conversationEventRepository.updateAccess(
            conversationId = event.conversationId,
            access = event.access,
            accessRoles = event.accessRole,
        ) { isAppsAccessEnabled ->
            systemMessageInserter.insertConversationAppsAccessChanged(
                eventId = event.id,
                conversationId = event.conversationId,
                senderUserId = event.qualifiedFrom,
                isAppsAccessEnabled = isAppsAccessEnabled,
            )
        }
}
