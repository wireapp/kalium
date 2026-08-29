/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant

/** Local persistence operation caused by incoming last-read messages. */
@InternalKaliumApi
public fun interface IncomingLastReadPersistence {
    public suspend fun updateReadDatesAndGetHasUnreadEvents(
        conversationDates: Map<ConversationId, Instant>,
    ): Either<StorageFailure, Map<ConversationId, Boolean>>
}

/** DAO-backed incoming last-read persistence shared by continuous and bounded event processing. */
@InternalKaliumApi
public class IncomingLastReadPersistenceImpl public constructor(
    private val conversationDAO: ConversationDAO,
) : IncomingLastReadPersistence {
    override suspend fun updateReadDatesAndGetHasUnreadEvents(
        conversationDates: Map<ConversationId, Instant>,
    ): Either<StorageFailure, Map<ConversationId, Boolean>> =
        wrapStorageRequest {
            conversationDAO.updateReadDatesAndGetHasUnreadEvents(conversationDates.mapKeys { it.key.toDao() })
        }.map { hasUnreadByConversation ->
            hasUnreadByConversation.mapKeys { it.key.toModel() }
        }
}
