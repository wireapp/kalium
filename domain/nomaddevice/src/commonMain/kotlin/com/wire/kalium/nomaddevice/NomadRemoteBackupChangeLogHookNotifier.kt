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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlinx.datetime.Clock

/**
 * Nomad-side [PersistenceEventHookNotifier] that writes syncable events to the remote backup changelog.
 */
public class NomadRemoteBackupChangeLogHookNotifier internal constructor(
    private val repository: NomadRemoteBackupChangeLogRepository
) : PersistenceEventHookNotifier {

    public constructor(
        userStorageProvider: UserStorageProvider,
        eventTimestampMsProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    ) : this(
        NomadRemoteBackupChangeLogDataSource(
            remoteBackupChangeLogDAOProvider = { userId ->
                userStorageProvider.get(userId)?.database?.remoteBackupChangeLogDAO
            },
            eventTimestampMsProvider = eventTimestampMsProvider,
        )
    )

    override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
        repository.logSyncableMessageUpsert(message, selfUserId)
    }

    override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
        repository.logSyncableMessageDelete(data, selfUserId)
    }

    override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
        repository.logSyncableReaction(data, selfUserId)
    }

    override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
        repository.logSyncableReadReceipt(data, selfUserId)
    }

    override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
        repository.logSyncableConversationDelete(data, selfUserId)
    }

    override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
        repository.logSyncableConversationClear(data, selfUserId)
    }
}
