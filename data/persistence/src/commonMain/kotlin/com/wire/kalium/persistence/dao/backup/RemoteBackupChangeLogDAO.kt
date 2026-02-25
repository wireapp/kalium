/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.backup

import com.wire.kalium.persistence.dao.QualifiedIDEntity

/**
 * Data Access Object for tracking changes that need to be synced to remote backup.
 * This changelog tracks WHAT changed, not the data itself.
 * Actual data is fetched from existing DB tables when syncing.
 */
interface RemoteBackupChangeLogDAO {

    /**
     * Log a message upsert (create or edit) event.
     */
    suspend fun logMessageUpsert(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long,
        messageTimestampMs: Long = timestampMs
    )

    /**
     * Log a message deletion event.
     */
    suspend fun logMessageDelete(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long
    )

    /**
     * Log a reactions sync event.
     * This means "sync all reactions for this message" - when any reaction changes.
     */
    suspend fun logReactionsSync(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long
    )

    /**
     * Log a read receipts sync event.
     * This means "sync all read receipts for this message" - when any receipt changes.
     */
    suspend fun logReadReceiptsSync(
        conversationId: QualifiedIDEntity,
        messageId: String,
        timestampMs: Long
    )

    /**
     * Log a conversation deletion event.
     * This clears all existing entries for the conversation and adds a single delete event.
     */
    suspend fun logConversationDelete(
        conversationId: QualifiedIDEntity,
        timestampMs: Long
    )

    /**
     * Log a conversation clear event.
     * This clears all existing entries for the conversation and adds a single clear event.
     */
    suspend fun logConversationClear(
        conversationId: QualifiedIDEntity,
        timestampMs: Long
    )

    /**
     * Get all pending changes ordered deterministically for replay.
     */
    suspend fun getPendingChanges(): List<ChangeLogEntry>

}
