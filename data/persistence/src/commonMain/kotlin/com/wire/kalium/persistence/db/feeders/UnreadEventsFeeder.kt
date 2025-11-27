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

package com.wire.kalium.persistence.db.feeders

import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Feeds synthetic unread events for performance testing of ConversationDetailsWithEvents.
 *
 * Populates:
 *  - UnreadEvent
 *  - UnreadEventCountsGrouped view automatically reflects aggregated counts
 *
 * Generates:
 *  - multiple events per message (MESSAGE, REPLY, MENTION…)
 *  - realistic creation_date distribution
 */
@DebugKaliumApi("Feeds synthetic unread events into local DB for performance testing only.")
class UnreadEventsFeeder(
    private val localDatabase: UserDatabaseBuilder,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend fun feed(conversationId: ConversationIDEntity) = withContext(dispatcher.io) {

        val sql = """
            WITH msgs AS (
                SELECT
                    id,
                    conversation_id,
                    creation_date,
                    (rowid % 7) AS bucket
                FROM Message
                WHERE conversation_id = ?
                ORDER BY rowid
            ),
            types AS (
                SELECT 'MESSAGE' AS type
                UNION ALL SELECT 'REPLY'
                UNION ALL SELECT 'MENTION'
                UNION ALL SELECT 'KNOCK'
                UNION ALL SELECT 'MISSED_CALL'
            )
            INSERT OR IGNORE INTO UnreadEvent (id, type, conversation_id, creation_date)
            SELECT
                m.id,
                t.type,
                m.conversation_id,
                m.creation_date
            FROM msgs m
            JOIN types t ON (m.bucket = (abs(random()) % 7));
        """.trimIndent()

        localDatabase.sqlDriver.execute(
            identifier = null,
            sql = sql,
            parameters = 1
        ) {
            bindString(0, conversationId.toString())
        }
    }
}
