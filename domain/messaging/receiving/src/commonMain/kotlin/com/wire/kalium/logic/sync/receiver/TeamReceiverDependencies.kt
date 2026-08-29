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
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.util.InternalKaliumApi

/** Message persistence operation shared by receiver implementations. */
@InternalKaliumApi
public fun interface EventMessagePersistence {
    public suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit>
}

/** User mutation required by team membership events. */
@InternalKaliumApi
public fun interface TeamEventUserRepository {
    public suspend fun markUserAsDeletedAndRemoveFromGroupConversations(
        userId: UserId
    ): Either<CoreFailure, List<ConversationId>>
}

/** Local team-event persistence shared by the app and future bounded receivers. */
@InternalKaliumApi
public class TeamEventUserRepositoryImpl public constructor(
    private val userDAO: UserDAO
) : TeamEventUserRepository {
    override suspend fun markUserAsDeletedAndRemoveFromGroupConversations(
        userId: UserId
    ): Either<CoreFailure, List<ConversationId>> = wrapStorageRequest {
        userDAO.markUserAsDeletedAndRemoveFromGroupConv(userId.toDao())
    }.map { conversationIds -> conversationIds.map { it.toModel() } }
}
