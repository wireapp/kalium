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
package com.wire.kalium.persistence.dao.pendingaction

import com.wire.kalium.persistence.PendingActionsQueries
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

internal class PendingActionDAOImpl(
    private val pendingActionsQueries: PendingActionsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : PendingActionDAO {

    override suspend fun upsert(actionType: PendingActionType, actionKey: String, payload: String?, createdAt: Long) {
        withContext(writeDispatcher.value) {
            pendingActionsQueries.upsert(
                action_type = actionType,
                action_key = actionKey,
                payload = payload,
                created_at = createdAt
            )
        }
    }

    override suspend fun getByActionType(actionType: PendingActionType): List<PendingActionEntity> = withContext(readDispatcher.value) {
        pendingActionsQueries.selectByActionType(
            action_type = actionType,
            mapper = ::PendingActionEntity
        ).executeAsList()
    }

    override suspend fun deleteByActionTypeAndKeys(actionType: PendingActionType, actionKeys: List<String>) {
        if (actionKeys.isEmpty()) return
        withContext(writeDispatcher.value) {
            pendingActionsQueries.deleteByActionTypeAndKeys(
                action_type = actionType,
                action_key = actionKeys
            )
        }
    }

    override suspend fun deleteByActionType(actionType: PendingActionType) {
        withContext(writeDispatcher.value) {
            pendingActionsQueries.deleteByActionType(action_type = actionType)
        }
    }
}
