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

import com.wire.kalium.persistence.dao.pendingaction.PendingActionDAO
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable
import kotlinx.datetime.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Mockable
internal interface PendingActionsRepository {
    suspend fun enqueuePendingOneOnOneResolution(userId: UserId)
    suspend fun getPendingOneOnOneResolutions(): List<UserId>
    suspend fun acknowledgePendingOneOnOneResolutions(userIds: List<UserId>)
}

internal class PersistentPendingActionsRepository(
    private val pendingActionDAO: PendingActionDAO,
) : PendingActionsRepository {
    private val mutex = Mutex()

    override suspend fun enqueuePendingOneOnOneResolution(userId: UserId) {
        mutex.withLock {
            pendingActionDAO.upsert(
                actionType = OneOnOneResolutionPendingAction.actionType,
                actionKey = OneOnOneResolutionPendingAction.actionKey(userId),
                payload = OneOnOneResolutionPendingAction.payload(userId),
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    override suspend fun getPendingOneOnOneResolutions(): List<UserId> = mutex.withLock {
        pendingActionDAO
            .getByActionType(OneOnOneResolutionPendingAction.actionType)
            .mapNotNull { OneOnOneResolutionPendingAction.userIdFromActionKey(it.actionKey) }
    }

    override suspend fun acknowledgePendingOneOnOneResolutions(userIds: List<UserId>) {
        val actionKeys = userIds.distinct().map(OneOnOneResolutionPendingAction::actionKey)
        if (actionKeys.isEmpty()) return
        mutex.withLock {
            pendingActionDAO.deleteByActionTypeAndKeys(
                actionType = OneOnOneResolutionPendingAction.actionType,
                actionKeys = actionKeys
            )
        }
    }
}
