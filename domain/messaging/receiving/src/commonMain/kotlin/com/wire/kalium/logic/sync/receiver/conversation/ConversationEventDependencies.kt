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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.LocalId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** User access required while applying incoming conversation events. */
public interface ConversationEventUserRepository {
    public suspend fun fetchUsersIfUnknownByIds(ids: Set<UserId>): Either<CoreFailure, Unit>

    public suspend fun observeUser(userId: UserId): Flow<User?>
}

/** User operations required while applying incoming member-join events. */
public interface MemberJoinEventUserRepository : ConversationEventUserRepository {
    public suspend fun updateActiveOneOnOneConversationIfNotSet(
        userId: UserId,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit>
}

/** User operations required while applying incoming member-leave events. */
public interface MemberLeaveEventUserRepository : ConversationEventUserRepository {
    public suspend fun markAsDeleted(userId: List<UserId>): Either<StorageFailure, Unit>

    public suspend fun isAtLeastOneUserATeamMember(
        userId: List<UserId>,
        teamId: TeamId,
    ): Either<StorageFailure, Boolean>
}

/** Conversation access required while applying an incoming member-join event. */
public interface MemberJoinEventRepository {
    public suspend fun getConversationById(conversationId: ConversationId): Either<StorageFailure, Conversation>

    public suspend fun isCellEnabled(conversationId: ConversationId): Either<StorageFailure, Boolean>
}

/** System-message operations required after receiving a new conversation. */
public interface NewConversationSystemMessagesCreator {
    public suspend fun conversationStartedUnverifiedWarning(
        conversationId: ConversationId,
        instant: Instant = Clock.System.now(),
    ): Either<CoreFailure, Unit>

    public suspend fun conversationStarted(
        creatorId: UserId,
        conversation: ConversationResponse,
        instant: Instant,
    ): Either<CoreFailure, Unit>

    public suspend fun conversationResolvedMembersAdded(
        conversationId: ConversationIDEntity,
        validUsers: List<UserId>,
        instant: Instant = Clock.System.now(),
    ): Either<CoreFailure, Unit>

    public suspend fun conversationReadReceiptStatus(
        conversation: ConversationResponse,
        instant: Instant,
    ): Either<CoreFailure, Unit>

    public suspend fun conversationAppsAccessIfEnabled(
        eventId: String = LocalId.generate(),
        conversationId: ConversationId,
        hasAppsAccessEnabled: Boolean,
        creatorId: UserId,
        type: ConversationEntity.Type,
    ): Either<CoreFailure, Unit>

    public suspend fun conversationCellAccessStatus(
        conversationId: ConversationId,
        conversationTeamId: String?,
        isCellEnabled: Boolean,
        instant: Instant = Clock.System.now(),
    ): Either<CoreFailure, Unit>
}

/** Conversation lookup required before applying a received deletion event. */
public fun interface DeletedConversationEventRepository {
    public suspend fun getConversationById(conversationId: ConversationId): Either<StorageFailure, Conversation>
}
