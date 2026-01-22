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
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for tracking changes that need to be synced to remote backup.
 * This changelog tracks WHAT changed, not the data itself.
 * Actual data is fetched from existing DB tables when syncing.
 */
@Mockable
interface RemoteBackupChangeLogDAO {

    /**
     * Log a message upsert (create or edit) event.
     */
    suspend fun logMessageUpsert(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestampMs: Long
    )

    /**
     * Log a message deletion event.
     */
    suspend fun logMessageDelete(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        timestampMs: Long
    )

    /**
     * Log a reactions sync event.
     * This means "sync all reactions for this message" - when any reaction changes.
     */
    suspend fun logReactionsSync(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
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
     * Observe all pending changes ordered by timestamp.
     */
    fun observePendingChanges(): Flow<List<ChangeLogEntry>>

    /**
     * Get all pending changes ordered by timestamp.
     */
    suspend fun getPendingChanges(): List<ChangeLogEntry>

    /**
     * Get pending changes filtered by event type.
     */
    suspend fun getChangesByEventType(eventType: ChangeLogEventType): List<ChangeLogEntry>

    /**
     * Get pending changes for a specific conversation.
     */
    suspend fun getChangesForConversation(conversationId: QualifiedIDEntity): List<ChangeLogEntry>

    /**
     * Delete all entries with timestamp up to and including the given value.
     * Used after successful sync to clean up processed entries.
     */
    suspend fun deleteProcessedEntries(upToTimestampMs: Long)

    /**
     * Delete a specific entry by its composite key.
     */
    suspend fun deleteEntry(
        conversationId: QualifiedIDEntity,
        messageNonce: String,
        eventType: ChangeLogEventType
    )
}
