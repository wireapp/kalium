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

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

private val nomadRemoteBackupChangeLogLogger = KaliumLogger(
    config = KaliumLogger.Config(initialLevel = KaliumLogLevel.WARN),
    tag = "NomadRemoteBackupChangeLog"
)

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
    coroutineScope: CoroutineScope,
    eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): (PersistedMessageData, UserId) -> Unit =
    createNomadRemoteBackupChangeLogCallbackInternal(
        remoteBackupChangeLogDAOProvider = { userId ->
            userStorageProvider.get(userId)?.database?.remoteBackupChangeLogDAO
        },
        coroutineScope = coroutineScope,
        eventTimestampMsProvider = eventTimestampMsProvider,
        warnLogger = { nomadRemoteBackupChangeLogLogger.w(it) },
        errorLogger = { message, throwable -> nomadRemoteBackupChangeLogLogger.e(message, throwable) }
    )

internal fun createNomadRemoteBackupChangeLogCallbackInternal(
    remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
    coroutineScope: CoroutineScope,
    eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    warnLogger: (String) -> Unit = { nomadRemoteBackupChangeLogLogger.w(it) },
    errorLogger: (String, Throwable) -> Unit = { message, throwable -> nomadRemoteBackupChangeLogLogger.e(message, throwable) },
): (PersistedMessageData, UserId) -> Unit = { message, selfUserId ->
    if (message.shouldLogMessageUpsert()) {
        val remoteBackupChangeLogDAO = remoteBackupChangeLogDAOProvider(selfUserId)
        if (remoteBackupChangeLogDAO == null) {
            warnLogger("Skipping MESSAGE_UPSERT changelog write: missing user storage for '${selfUserId.toLogString()}'.")
        } else {
            val eventTimestampMs = eventTimestampMsProvider()
            val messageTimestampMs = message.date.toEpochMilliseconds()

            coroutineScope.launch {
                runCatching {
                    remoteBackupChangeLogDAO.logMessageUpsert(
                        conversationId = message.conversationId.toDao(),
                        messageId = message.messageId,
                        timestampMs = eventTimestampMs,
                        messageTimestampMs = messageTimestampMs
                    )
                }.onFailure { throwable ->
                    errorLogger(
                        "Failed to write MESSAGE_UPSERT changelog for conversation '${message.conversationId.toLogString()}' and message '${message.messageId}'.",
                        throwable
                    )
                }
            }
        }
    }
}

private fun PersistedMessageData.shouldLogMessageUpsert(): Boolean = when (val messageContent = content) {
    is MessageContent.Text,
    is MessageContent.Asset,
    is MessageContent.Location -> true

    is MessageContent.Multipart -> messageContent.hasSupportedPartForChangelog()
    else -> false
}

private fun MessageContent.Multipart.hasSupportedPartForChangelog(): Boolean =
    value != null || attachments.any { it is AssetContent }

private fun QualifiedID.toDao(): QualifiedIDEntity = QualifiedIDEntity(value, domain)
