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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.UserDAO

/** Local user persistence required while applying team and federation events. */
public interface EventUserPersistence {
    public suspend fun markUserAsDeletedAndRemoveFromGroupConversations(
        userId: UserId
    ): Either<CoreFailure, List<ConversationId>>

    public suspend fun defederateUser(userId: UserId): Either<CoreFailure, Unit>
}

/** DAO-backed user persistence shared by continuous and bounded event processing. */
public class EventUserPersistenceImpl public constructor(
    private val userDAO: UserDAO
) : EventUserPersistence {
    override suspend fun markUserAsDeletedAndRemoveFromGroupConversations(
        userId: UserId
    ): Either<CoreFailure, List<ConversationId>> = wrapStorageRequest {
        userDAO.markUserAsDeletedAndRemoveFromGroupConv(userId.toDao())
    }.map { conversationIds -> conversationIds.map { it.toModel() } }

    override suspend fun defederateUser(userId: UserId): Either<CoreFailure, Unit> = wrapStorageRequest {
        userDAO.markUserAsDefederated(userId.toDao())
    }
}
