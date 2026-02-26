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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.datetime.Clock

/**
 * Factory used with [NomadPersistMessageHookNotifier]:
 *
 * ```
 * val callback = createNomadRemoteBackupChangeLogCallback(...)
 * val notifier = NomadPersistMessageHookNotifier(callback)
 * ```
 */
public fun createNomadRemoteBackupChangeLogCallback(
    userStorageProvider: UserStorageProvider,
    eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): suspend (PersistedMessageData, UserId) -> Unit =
    createNomadRemoteBackupChangeLogCallbackInternal(
        remoteBackupChangeLogDAOProvider = { userId ->
            userStorageProvider.get(userId)?.database?.remoteBackupChangeLogDAO
        },
        eventTimestampMsProvider = eventTimestampMsProvider,
    )

internal fun createNomadRemoteBackupChangeLogCallbackInternal(
    remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
    eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): suspend (PersistedMessageData, UserId) -> Unit {
    val repository = NomadRemoteBackupChangeLogDataSource(
        remoteBackupChangeLogDAOProvider = remoteBackupChangeLogDAOProvider,
        eventTimestampMsProvider = eventTimestampMsProvider,
    )
    return { message, selfUserId ->
        repository.logSyncableMessageUpsert(message, selfUserId)
    }
}

internal class NomadRemoteBackupChangeLogDataSource(
    private val remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
    private val eventTimestampMsProvider: () -> Long,
) : NomadRemoteBackupChangeLogRepository {

    override suspend fun logSyncableMessageUpsert(message: PersistedMessageData, selfUserId: UserId): Either<StorageFailure, Unit> =
        when (val dao = resolveDao(message, selfUserId)) {
            null -> Unit.right()
            else -> persistChangeLog(dao, message)
        }

    private fun resolveDao(message: PersistedMessageData, selfUserId: UserId): RemoteBackupChangeLogDAO? {
        if (!message.shouldLogMessageUpsert()) return null
        return remoteBackupChangeLogDAOProvider(selfUserId).also { dao ->
            if (dao == null) {
                nomadLogger.w("Skipping MESSAGE_UPSERT changelog write: missing user storage for '${selfUserId.toLogString()}'.")
            }
        }
    }

    private suspend fun persistChangeLog(
        dao: RemoteBackupChangeLogDAO,
        message: PersistedMessageData,
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        dao.logMessageUpsert(
            conversationId = message.conversationId.toDao(),
            messageId = message.messageId,
            timestampMs = eventTimestampMsProvider(),
            messageTimestampMs = message.date.toEpochMilliseconds()
        )
    }.onFailure { _ ->
        nomadLogger.i(
            "Failed to write MESSAGE_UPSERT changelog for conversation " +
                    "'${message.conversationId.toLogString()}' and message '${message.messageId}'."
        )
    }
}

private fun PersistedMessageData.shouldLogMessageUpsert(): Boolean = when (val messageContent = content) {
    is MessageContent.Text,
    is MessageContent.Asset,
    is MessageContent.Location -> true

    is MessageContent.Multipart -> messageContent.hasSupportedPartForChangelog()

    else -> false
}

private fun MessageContent.Multipart.hasSupportedPartForChangelog(): Boolean {
    // `value` is the textual part of a multipart message.
    val hasTextPart = value != null
    // Only regular message assets are currently syncable in this changelog flow.
    // Multipart payloads with only CellAssetContent attachments are intentionally skipped.
    val hasSyncableAssetPart = attachments.any { it is AssetContent }
    return hasTextPart || hasSyncableAssetPart
}

// TODO: delete this one once the logic mappers are moved to a shared module
private fun QualifiedID.toDao(): QualifiedIDEntity = QualifiedIDEntity(value, domain)
