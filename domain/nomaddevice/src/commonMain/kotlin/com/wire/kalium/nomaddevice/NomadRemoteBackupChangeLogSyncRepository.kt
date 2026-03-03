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

package com.wire.kalium.nomaddevice

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.persistence.dao.backup.ChangeLogEntry
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO

/**
 * Repository for sync-page operations between remote-backup changelog and Nomad API.
 */
internal interface NomadRemoteBackupChangeLogSyncRepository {
    suspend fun getLastPendingChangesBatch(selfUserId: UserId, limit: Long): Either<CoreFailure, ChangeLogSyncBatch>
    suspend fun postMessageEvents(selfUserId: UserId, request: NomadMessageEventsRequest): Either<CoreFailure, Unit>
    suspend fun deleteChanges(selfUserId: UserId, changes: List<ChangeLogEntry>): Either<CoreFailure, Unit>
}

internal class NomadRemoteBackupChangeLogSyncDataSource(
    private val remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
    private val nomadDeviceSyncApiProvider: (UserId) -> NomadDeviceSyncApi,
) : NomadRemoteBackupChangeLogSyncRepository {

    override suspend fun getLastPendingChangesBatch(selfUserId: UserId, limit: Long): Either<CoreFailure, ChangeLogSyncBatch> {
        val dao = resolveDaoForUser(selfUserId, operation = "read")
            ?: return ChangeLogSyncBatch(events = emptyList(), conversationLastReads = emptyList()).right()
        return wrapStorageRequest { dao.getLastPendingChangesBatch(limit) }
    }

    override suspend fun postMessageEvents(
        selfUserId: UserId,
        request: NomadMessageEventsRequest
    ): Either<CoreFailure, Unit> = wrapApiRequest {
        nomadDeviceSyncApiProvider(selfUserId).postMessageEvents(request)
    }

    override suspend fun deleteChanges(selfUserId: UserId, changes: List<ChangeLogEntry>): Either<CoreFailure, Unit> {
        val dao = resolveDaoForUser(selfUserId, operation = "delete") ?: return Unit.right()
        return wrapStorageRequest { dao.deleteChanges(changes) }
    }

    private fun resolveDaoForUser(selfUserId: UserId, operation: String): RemoteBackupChangeLogDAO? {
        return remoteBackupChangeLogDAOProvider(selfUserId).also { dao ->
            if (dao == null) {
                nomadLogger.w(
                    "Skipping remote-backup sync $operation: missing user storage for '${selfUserId.toLogString()}'."
                )
            }
        }
    }
}
