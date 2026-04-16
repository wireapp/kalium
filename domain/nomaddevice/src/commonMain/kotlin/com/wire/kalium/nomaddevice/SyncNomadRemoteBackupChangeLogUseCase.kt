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
import com.wire.kalium.common.logger.nomadTrace
import com.wire.kalium.common.logger.nomadTraceTextPreview
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch
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

    public suspend operator fun invoke(selfUserId: UserId): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> =
        when (val batchResult = repository.getLastPendingChangesBatch(selfUserId, pageSize)) {
            is Either.Left -> batchResult.value.left()
            is Either.Right -> {
                nomadLogger.nomadTrace(
                    stage = "nomad.messages.upload.batch.loaded",
                    fields = mapOf(
                        "userId" to selfUserId.toLogString(),
                        "eventsCount" to batchResult.value.events.size,
                        "metadataCount" to batchResult.value.conversationMetadata.size,
                        "pageSize" to pageSize
                    )
                )
                syncBatch(selfUserId, batchResult.value)
            }
        }

    private suspend fun syncBatch(
        selfUserId: UserId,
        batch: ChangeLogSyncBatch
    ): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> =
        if (batch.events.isEmpty()) {
            NomadRemoteBackupChangeLogSyncResult(syncedEntries = 0, postedEvents = 0).right()
        } else {
            syncNonEmptyBatch(selfUserId, batch)
        }

    private suspend fun syncNonEmptyBatch(
        selfUserId: UserId,
        batch: ChangeLogSyncBatch
    ): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> {
        val mappedEvents = eventMapper.mapBatchToApiEvents(batch)
        nomadLogger.nomadTrace(
            stage = "nomad.messages.upload.batch.mapped",
            fields = mapOf(
                "userId" to selfUserId.toLogString(),
                "eventsCount" to batch.events.size,
                "mappedEventsCount" to mappedEvents.size
            )
        )
        batch.events.forEach { event ->
            if (event is com.wire.kalium.persistence.dao.backup.ChangeLogSyncEvent.MessageUpsert) {
                nomadLogger.nomadTrace(
                    stage = "nomad.messages.upload.message",
                    fields = mapOf(
                        "userId" to selfUserId.toLogString(),
                        "conversationId" to event.conversationId.toLogString(),
                        "messageId" to event.messageId,
                        "contentType" to event.message?.contentType?.name,
                        "textPreview" to nomadTraceTextPreview(event.message?.textForNomadTrace())
                    )
                )
            }
        }
        return when (val postResult = postMappedEvents(selfUserId, batch, mappedEvents)) {
            is Either.Left -> postResult.value.left()
            is Either.Right -> deleteChanges(
                selfUserId = selfUserId,
                batch = batch,
                postedEvents = mappedEvents.size
            )
        }
    }

    private suspend fun postMappedEvents(
        selfUserId: UserId,
        batch: ChangeLogSyncBatch,
        mappedEvents: List<NomadMessageEvent>,
    ): Either<CoreFailure, Unit> =
        if (mappedEvents.isNotEmpty()) {
            nomadLogger.nomadTrace(
                stage = "nomad.messages.upload.batch.post.start",
                fields = mapOf(
                    "userId" to selfUserId.toLogString(),
                    "eventsCount" to batch.events.size,
                    "mappedEventsCount" to mappedEvents.size
                )
            )
            repository.postMessageEvents(selfUserId, NomadMessageEventsRequest(events = mappedEvents))
                .also { result ->
                    when (result) {
                        is Either.Left -> nomadLogger.nomadTrace(
                            stage = "nomad.messages.upload.batch.post.failure",
                            fields = mapOf(
                                "userId" to selfUserId.toLogString(),
                                "error" to result.value
                            )
                        )

                        is Either.Right -> nomadLogger.nomadTrace(
                            stage = "nomad.messages.upload.batch.post.success",
                            fields = mapOf(
                                "userId" to selfUserId.toLogString(),
                                "mappedEventsCount" to mappedEvents.size
                            )
                        )
                    }
                }
        } else {
            nomadLogger.w(
                "Dropping ${batch.events.size} changelog entries that cannot be mapped to Nomad events " +
                        "for '${selfUserId.toLogString()}'."
            )
            Unit.right()
        }

    private suspend fun deleteChanges(
        selfUserId: UserId,
        batch: ChangeLogSyncBatch,
        postedEvents: Int,
    ): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> {
        val changesToDelete = batch.events.map { it.change }
        return when (val deleteResult = repository.deleteChanges(selfUserId, changesToDelete)) {
            is Either.Left -> deleteResult.value.left()
            is Either.Right -> {
                nomadLogger.nomadTrace(
                    stage = "nomad.messages.upload.batch.delete.success",
                    fields = mapOf(
                        "userId" to selfUserId.toLogString(),
                        "syncedEntries" to changesToDelete.size,
                        "postedEvents" to postedEvents
                    )
                )
                NomadRemoteBackupChangeLogSyncResult(
                    syncedEntries = changesToDelete.size,
                    postedEvents = postedEvents
                ).right()
            }
        }
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

internal fun UserId.toNetworkUserId(): com.wire.kalium.network.api.model.UserId =
    com.wire.kalium.network.api.model.QualifiedID(value = value, domain = domain)

private fun com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity.textForNomadTrace(): String? =
    when (this) {
        is com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity.Text -> text
        is com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity.Multipart -> text
        else -> null
    }
