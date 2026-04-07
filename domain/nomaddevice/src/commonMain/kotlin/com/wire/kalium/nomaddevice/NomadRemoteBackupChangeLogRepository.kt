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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData

/**
 * Repository for writing persisted message hook events to remote backup changelog.
 */
internal interface NomadRemoteBackupChangeLogRepository {
    suspend fun logSyncableMessageUpsert(message: PersistedMessageData, selfUserId: UserId): Either<StorageFailure, Unit>
    suspend fun logSyncableMessageDelete(data: MessageDeleteEventData, selfUserId: UserId): Either<StorageFailure, Unit>
    suspend fun logSyncableReaction(data: ReactionEventData, selfUserId: UserId): Either<StorageFailure, Unit>
    suspend fun logSyncableReadReceipt(data: ReadReceiptEventData, selfUserId: UserId): Either<StorageFailure, Unit>
    suspend fun logSyncableConversationDelete(data: ConversationDeleteEventData, selfUserId: UserId): Either<StorageFailure, Unit>
    suspend fun logSyncableConversationClear(data: ConversationClearEventData, selfUserId: UserId): Either<StorageFailure, Unit>
}
