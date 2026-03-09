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
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Factory used with [NomadRemoteBackupChangeLogHookNotifier]:
 *
 * ```
 * val notifier = createNomadRemoteBackupChangeLogHookNotifier(...)
 * ```
 */
public fun createNomadRemoteBackupChangeLogHookNotifier(
    userStorageProvider: UserStorageProvider,
    eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): PersistenceEventHookNotifier =
    createNomadRemoteBackupChangeLogHookNotifierInternal(
        remoteBackupChangeLogDAOProvider = { userId ->
            userStorageProvider.get(userId)?.database?.remoteBackupChangeLogDAO
        },
        eventTimestampMsProvider = eventTimestampMsProvider,
    )

/**
 * Creates a changelog hook that is permanently bound to a single user session.
 *
 * This avoids sharing one generic notifier across multiple accounts in Logic. Any event coming from a
 * different user session is ignored, while matching events keep using the existing Nomad changelog flow.
 */
public fun createUserScopedNomadRemoteBackupChangeLogHookNotifier(
    selfUserId: UserId,
    userStorageProvider: UserStorageProvider,
    eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): PersistenceEventHookNotifier =
    UserScopedNomadPersistenceEventHookNotifier(
        selfUserId = selfUserId,
        delegate = createNomadRemoteBackupChangeLogHookNotifier(
            userStorageProvider = userStorageProvider,
            eventTimestampMsProvider = eventTimestampMsProvider,
        )
    )

internal fun createNomadRemoteBackupChangeLogHookNotifierInternal(
    remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
    eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): PersistenceEventHookNotifier {
    val repository = NomadRemoteBackupChangeLogDataSource(
        remoteBackupChangeLogDAOProvider = remoteBackupChangeLogDAOProvider,
        eventTimestampMsProvider = eventTimestampMsProvider,
    )
    return NomadRemoteBackupChangeLogHookNotifier(repository)
}

internal class NomadRemoteBackupChangeLogDataSource(
    private val remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
    private val eventTimestampMsProvider: () -> Long,
) : NomadRemoteBackupChangeLogRepository {

    @Suppress("ReturnCount")
    override suspend fun logSyncableMessageUpsert(message: PersistedMessageData, selfUserId: UserId): Either<StorageFailure, Unit> {
        if (!message.shouldLogMessageUpsert()) return Unit.right()
        val dao = resolveDaoForUser(selfUserId, "MESSAGE_UPSERT") ?: return Unit.right()
        return wrapStorageRequest {
            dao.logMessageUpsert(
                conversationId = message.conversationId.toDao(),
                messageId = message.messageId,
                timestampMs = eventTimestampMsProvider(),
                messageTimestampMs = message.date.toEpochMilliseconds()
            )
        }.onFailure { _ ->
            nomadLogger.e(
                "Failed to write MESSAGE_UPSERT changelog for conversation " +
                    "'${message.conversationId.toLogString()}' and message '${message.messageId}'.",
                RuntimeException("MESSAGE_UPSERT failed")
            )
        }
    }

    override suspend fun logSyncableMessageDelete(data: MessageDeleteEventData, selfUserId: UserId): Either<StorageFailure, Unit> {
        val dao = resolveDaoForUser(selfUserId, "MESSAGE_DELETE") ?: return Unit.right()
        return wrapStorageRequest {
            dao.logMessageDelete(
                conversationId = data.conversationId.toDao(),
                messageId = data.messageId,
                timestampMs = eventTimestampMsProvider()
            )
        }.onFailure { _ ->
            nomadLogger.e(
                "Failed to write MESSAGE_DELETE changelog for conversation " +
                    "'${data.conversationId.toLogString()}' and message '${data.messageId}'.",
                RuntimeException("MESSAGE_DELETE failed")
            )
        }
    }

    override suspend fun logSyncableReaction(data: ReactionEventData, selfUserId: UserId): Either<StorageFailure, Unit> {
        val dao = resolveDaoForUser(selfUserId, "REACTIONS_SYNC") ?: return Unit.right()
        return wrapStorageRequest {
            dao.logReactionsSync(
                conversationId = data.conversationId.toDao(),
                messageId = data.messageId,
                timestampMs = eventTimestampMsProvider()
            )
        }.onFailure { _ ->
            nomadLogger.e(
                "Failed to write REACTIONS_SYNC changelog for conversation " +
                    "'${data.conversationId.toLogString()}' and message '${data.messageId}'.",
                RuntimeException("REACTIONS_SYNC failed")
            )
        }
    }

    override suspend fun logSyncableReadReceipt(data: ReadReceiptEventData, selfUserId: UserId): Either<StorageFailure, Unit> {
        val dao = resolveDaoForUser(selfUserId, "READ_RECEIPTS_SYNC") ?: return Unit.right()
        var result: Either<StorageFailure, Unit> = Unit.right()
        for (messageId in data.messageIds) {
            val r = wrapStorageRequest {
                dao.logReadReceiptsSync(
                    conversationId = data.conversationId.toDao(),
                    messageId = messageId,
                    timestampMs = eventTimestampMsProvider()
                )
            }.onFailure { _ ->
                nomadLogger.e(
                    "Failed to write READ_RECEIPTS_SYNC changelog for conversation " +
                        "'${data.conversationId.toLogString()}' and message '$messageId'.",
                    RuntimeException("READ_RECEIPTS_SYNC failed")
                )
            }
            if (r is Either.Left) result = r
        }
        return result
    }

    override suspend fun logSyncableConversationDelete(
        data: ConversationDeleteEventData,
        selfUserId: UserId
    ): Either<StorageFailure, Unit> {
        val dao = resolveDaoForUser(selfUserId, "CONVERSATION_DELETE") ?: return Unit.right()
        return wrapStorageRequest {
            dao.logConversationDelete(
                conversationId = data.conversationId.toDao(),
                timestampMs = eventTimestampMsProvider()
            )
        }.onFailure { _ ->
            nomadLogger.e(
                "Failed to write CONVERSATION_DELETE changelog for conversation " +
                    "'${data.conversationId.toLogString()}'.",
                RuntimeException("CONVERSATION_DELETE failed")
            )
        }
    }

    override suspend fun logSyncableConversationClear(
        data: ConversationClearEventData,
        selfUserId: UserId
    ): Either<StorageFailure, Unit> {
        val dao = resolveDaoForUser(selfUserId, "CONVERSATION_CLEAR") ?: return Unit.right()
        return wrapStorageRequest {
            dao.logConversationClear(
                conversationId = data.conversationId.toDao(),
                timestampMs = eventTimestampMsProvider()
            )
        }.onFailure { _ ->
            nomadLogger.e(
                "Failed to write CONVERSATION_CLEAR changelog for conversation " +
                    "'${data.conversationId.toLogString()}'.",
                RuntimeException("CONVERSATION_CLEAR failed")
            )
        }
    }

    private fun resolveDaoForUser(selfUserId: UserId, eventTag: String): RemoteBackupChangeLogDAO? {
        return remoteBackupChangeLogDAOProvider(selfUserId).also { dao ->
            if (dao == null) {
                nomadLogger.w("Skipping $eventTag changelog write: missing user storage for '${selfUserId.toLogString()}'.")
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

private fun MessageContent.Multipart.hasSupportedPartForChangelog(): Boolean {
    // `value` is the textual part of a multipart message.
    val hasTextPart = value != null
    // Only regular message assets are currently syncable in this changelog flow.
    // Multipart payloads with only CellAssetContent attachments are intentionally skipped.
    val hasSyncableAssetPart = attachments.any { it is AssetContent }
    return hasTextPart || hasSyncableAssetPart
}

internal class UserScopedNomadPersistenceEventHookNotifier(
    private val selfUserId: UserId,
    private val delegate: PersistenceEventHookNotifier,
) : PersistenceEventHookNotifier {

    override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
        if (selfUserId == this.selfUserId) {
            safeInvoke("PersistMessage") { it.onMessagePersisted(message, this.selfUserId) }
        }
    }

    override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
        if (selfUserId == this.selfUserId) {
            safeInvoke("MessageDelete") { it.onMessageDeleted(data, this.selfUserId) }
        }
    }

    override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
        if (selfUserId == this.selfUserId) {
            safeInvoke("ReactionPersist") { it.onReactionPersisted(data, this.selfUserId) }
        }
    }

    override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
        if (selfUserId == this.selfUserId) {
            safeInvoke("ReadReceipt") { it.onReadReceiptPersisted(data, this.selfUserId) }
        }
    }

    override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
        if (selfUserId == this.selfUserId) {
            safeInvoke("ConversationDelete") { it.onConversationDeleted(data, this.selfUserId) }
        }
    }

    override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
        if (selfUserId == this.selfUserId) {
            safeInvoke("ConversationClear") { it.onConversationCleared(data, this.selfUserId) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun safeInvoke(tag: String, block: (PersistenceEventHookNotifier) -> Unit) {
        try {
            block(delegate)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            kaliumLogger.w("User-scoped $tag hook execution failed", exception)
        }
    }
}

// TODO: delete this one once the logic mappers are moved to a shared module
private fun QualifiedID.toDao(): QualifiedIDEntity = QualifiedIDEntity(value, domain)
