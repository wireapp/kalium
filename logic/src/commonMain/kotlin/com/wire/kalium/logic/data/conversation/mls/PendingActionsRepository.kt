/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation.mls

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import io.mockative.Mockable
import kotlinx.datetime.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Mockable
internal interface PendingActionsRepository {
    suspend fun enqueuePendingOneOnOneResolution(userId: UserId)
    suspend fun getPendingOneOnOneResolutions(): List<UserId>
    suspend fun acknowledgePendingOneOnOneResolutions(userIds: List<UserId>)
    suspend fun enqueuePendingMLSGroupJoin(conversationId: ConversationId)
    suspend fun getPendingMLSGroupJoins(): List<ConversationId>
    suspend fun acknowledgePendingMLSGroupJoins(conversationIds: List<ConversationId>)
}

internal class PersistentPendingActionsRepository(
    private val pendingActionDAO: PendingActionDAO,
) : PendingActionsRepository {
    private val mutex = Mutex()

    override suspend fun enqueuePendingOneOnOneResolution(userId: UserId) {
        mutex.withLock {
            pendingActionDAO.upsert(
                actionType = ONE_ON_ONE_RESOLUTION_ACTION_TYPE,
                qualifiedId = userId.toDao(),
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    override suspend fun getPendingOneOnOneResolutions(): List<UserId> = mutex.withLock {
        pendingActionDAO
            .getByActionType(ONE_ON_ONE_RESOLUTION_ACTION_TYPE)
            .map { it.qualifiedId.toModel() }
    }

    override suspend fun acknowledgePendingOneOnOneResolutions(userIds: List<UserId>) {
        val qualifiedIds = userIds.distinct().map { it.toDao() }
        if (qualifiedIds.isEmpty()) return
        mutex.withLock {
            pendingActionDAO.deleteByActionTypeAndIds(
                actionType = ONE_ON_ONE_RESOLUTION_ACTION_TYPE,
                qualifiedIds = qualifiedIds
            )
        }
    }

    override suspend fun enqueuePendingMLSGroupJoin(conversationId: ConversationId) {
        mutex.withLock {
            pendingActionDAO.upsert(
                actionType = MLS_GROUP_JOIN_ACTION_TYPE,
                qualifiedId = conversationId.toDao(),
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    override suspend fun getPendingMLSGroupJoins(): List<ConversationId> = mutex.withLock {
        pendingActionDAO
            .getByActionType(MLS_GROUP_JOIN_ACTION_TYPE)
            .map { it.qualifiedId.toModel() }
    }

    override suspend fun acknowledgePendingMLSGroupJoins(conversationIds: List<ConversationId>) {
        val qualifiedIds = conversationIds.distinct().map { it.toDao() }
        if (qualifiedIds.isEmpty()) return
        mutex.withLock {
            pendingActionDAO.deleteByActionTypeAndIds(
                actionType = MLS_GROUP_JOIN_ACTION_TYPE,
                qualifiedIds = qualifiedIds
            )
        }
    }

    private companion object {
        val ONE_ON_ONE_RESOLUTION_ACTION_TYPE = PendingActionType.RESOLVE_ONE_ON_ONE_CONVERSATION
        val MLS_GROUP_JOIN_ACTION_TYPE = PendingActionType.JOIN_MLS_GROUP_CONVERSATION
    }
}
