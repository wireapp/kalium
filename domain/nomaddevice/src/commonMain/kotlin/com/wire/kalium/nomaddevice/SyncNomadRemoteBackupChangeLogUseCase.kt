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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import com.wire.kalium.userstorage.di.UserStorageProvider

public data class NomadRemoteBackupChangeLogSyncResult(
    val syncedEntries: Int,
    val postedEvents: Int,
)

/**
 * Use case that reads a remote-backup changelog page from DB, maps it to Nomad API events,
 * posts it to Nomad, and removes that page from changelog only after a successful post.
 */
public class SyncNomadRemoteBackupChangeLogUseCase internal constructor(
    private val repository: NomadRemoteBackupChangeLogSyncRepository,
    private val eventMapper: NomadRemoteBackupChangeLogEventMapper = NomadRemoteBackupChangeLogEventMapper(),
    private val pageSize: Long = DEFAULT_PAGE_SIZE,
) {

    internal constructor(
        remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
        nomadDeviceSyncApiProvider: (UserId) -> NomadDeviceSyncApi,
        pageSize: Long = DEFAULT_PAGE_SIZE,
    ) : this(
        repository = NomadRemoteBackupChangeLogSyncDataSource(
            remoteBackupChangeLogDAOProvider = remoteBackupChangeLogDAOProvider,
            nomadDeviceSyncApiProvider = nomadDeviceSyncApiProvider
        ),
        pageSize = pageSize
    )

    public constructor(
        userStorageProvider: UserStorageProvider,
        nomadAuthenticatedNetworkAccess: NomadAuthenticatedNetworkAccess,
        pageSize: Long = DEFAULT_PAGE_SIZE,
    ) : this(
        repository = NomadRemoteBackupChangeLogSyncDataSource(
            remoteBackupChangeLogDAOProvider = { userId ->
                userStorageProvider.get(userId)?.database?.remoteBackupChangeLogDAO
            },
            nomadDeviceSyncApiProvider = { userId ->
                nomadAuthenticatedNetworkAccess.nomadDeviceSyncApi(userId.toNetworkUserId())
            }
        ),
        pageSize = pageSize
    )

    public suspend operator fun invoke(selfUserId: UserId): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> {
        val batch = when (val result = repository.getLastPendingChangesBatch(selfUserId, pageSize)) {
            is Either.Left -> return result.value.left()
            is Either.Right -> result.value
        }

        if (batch.events.isEmpty()) {
            return NomadRemoteBackupChangeLogSyncResult(syncedEntries = 0, postedEvents = 0).right()
        }

        val mappedEvents = eventMapper.mapBatchToApiEvents(batch)
        if (mappedEvents.isNotEmpty()) {
            val request = NomadMessageEventsRequest(events = mappedEvents)
            when (val result = repository.postMessageEvents(selfUserId, request)) {
                is Either.Left -> return result.value.left()
                is Either.Right -> Unit
            }
        } else {
            nomadLogger.w(
                "Dropping ${batch.events.size} changelog entries that cannot be mapped to Nomad events " +
                        "for '${selfUserId.toLogString()}'."
            )
        }

        val changesToDelete = batch.events.map { it.change }
        when (val result = repository.deleteChanges(selfUserId, changesToDelete)) {
            is Either.Left -> return result.value.left()
            is Either.Right -> Unit
        }

        return NomadRemoteBackupChangeLogSyncResult(
            syncedEntries = changesToDelete.size,
            postedEvents = mappedEvents.size
        ).right()
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE: Long = 100
    }
}

@Deprecated(
    message = "Use SyncNomadRemoteBackupChangeLogUseCase",
    replaceWith = ReplaceWith("SyncNomadRemoteBackupChangeLogUseCase")
)
public typealias NomadRemoteBackupChangeLogSyncer = SyncNomadRemoteBackupChangeLogUseCase

private fun UserId.toNetworkUserId(): com.wire.kalium.network.api.model.UserId =
    com.wire.kalium.network.api.model.QualifiedID(value = value, domain = domain)
