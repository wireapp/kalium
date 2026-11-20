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

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.PendingActionsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

internal class PendingActionDAOImpl(
    private val pendingActionsQueries: PendingActionsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : PendingActionDAO {

    override suspend fun hasMLSGroupRecovery(qualifiedId: QualifiedIDEntity): Boolean = withContext(readDispatcher.value) {
        pendingActionsQueries.selectMLSRecoveryById(qualifiedId).awaitAsList().isNotEmpty()
    }

    override suspend fun postpone(actionType: PendingActionType, action: PendingActionEntity, nextAttempt: Long) =
        withContext(writeDispatcher.value) {
            pendingActionsQueries.postpone(nextAttempt, actionType, action.qualifiedId, action.createdAt)
            Unit
        }

    override suspend fun requestMLSGroupJoin(qualifiedId: QualifiedIDEntity, createdAt: Long, evidence: Boolean): Boolean =
        withContext(writeDispatcher.value) {
            pendingActionsQueries.transactionWithResult {
                val join = PendingActionType.RECOVER_MLS_GROUP_CONVERSATION
                val pause = PendingActionType.PAUSE_MLS_GROUP_JOIN
                val queued = pendingActionsQueries.selectByTypeAndId(join, qualifiedId, ::PendingActionEntity).awaitAsOneOrNull()
                val paused = pendingActionsQueries.selectByTypeAndId(pause, qualifiedId, ::PendingActionEntity).awaitAsOneOrNull()
                if (evidence) {
                    pendingActionsQueries.upsert(join, qualifiedId, createdAt)
                    if (paused != null) pendingActionsQueries.acknowledge(pause, qualifiedId, paused.createdAt)
                    queued == null && paused == null
                } else if (paused == null) {
                    pendingActionsQueries.insertIfAbsent(join, qualifiedId, createdAt)
                    pendingActionsQueries.selectChanges().awaitAsOne() > 0
                } else false
            }
        }

    override suspend fun pauseMLSGroupJoin(action: PendingActionEntity) = withContext(writeDispatcher.value) {
        pendingActionsQueries.transaction {
            val join = PendingActionType.RECOVER_MLS_GROUP_CONVERSATION
            val current = pendingActionsQueries.selectByTypeAndId(join, action.qualifiedId, ::PendingActionEntity).awaitAsOneOrNull()
            if (current == action) {
                pendingActionsQueries.upsert(PendingActionType.PAUSE_MLS_GROUP_JOIN, action.qualifiedId, action.createdAt)
                pendingActionsQueries.acknowledge(join, action.qualifiedId, action.createdAt)
            }
        }
    }

    override suspend fun upsert(actionType: PendingActionType, qualifiedId: QualifiedIDEntity, createdAt: Long) {
        withContext(writeDispatcher.value) {
            pendingActionsQueries.upsert(
                action_type = actionType,
                qualified_id = qualifiedId,
                created_at = createdAt
            )
        }
    }

    override suspend fun getByActionType(actionType: PendingActionType): List<PendingActionEntity> = withContext(readDispatcher.value) {
        pendingActionsQueries.selectByActionType(
            action_type = actionType,
            mapper = ::PendingActionEntity
        ).awaitAsList()
    }

    override suspend fun deleteByActionTypeAndIds(actionType: PendingActionType, qualifiedIds: List<QualifiedIDEntity>) {
        if (qualifiedIds.isEmpty()) return
        withContext(writeDispatcher.value) {
            pendingActionsQueries.deleteByActionTypeAndIds(
                action_type = actionType,
                qualified_id = qualifiedIds
            )
        }
    }

    override suspend fun deleteByActionType(actionType: PendingActionType) {
        withContext(writeDispatcher.value) {
            pendingActionsQueries.deleteByActionType(action_type = actionType)
        }
    }

    override suspend fun acknowledge(actionType: PendingActionType, action: PendingActionEntity) {
        withContext(writeDispatcher.value) {
            pendingActionsQueries.acknowledge(actionType, action.qualifiedId, action.createdAt)
        }
    }
}
