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

package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.UserReactions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant

/** Local persistence operation caused by an incoming reaction message. */
@InternalKaliumApi
public fun interface IncomingReactionPersistence {
    public suspend fun updateReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        instant: Instant,
        userReactions: UserReactions,
    ): Either<StorageFailure, Unit>
}

/** DAO-backed incoming-reaction persistence shared by continuous and bounded event processing. */
@InternalKaliumApi
public class IncomingReactionPersistenceImpl public constructor(
    private val reactionDAO: ReactionDAO,
) : IncomingReactionPersistence {
    override suspend fun updateReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        instant: Instant,
        userReactions: UserReactions,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        reactionDAO.updateReactions(
            originalMessageId = originalMessageId,
            conversationId = conversationId.toDao(),
            senderUserId = senderUserId.toDao(),
            instant = instant,
            reactions = userReactions,
        )
    }
}
