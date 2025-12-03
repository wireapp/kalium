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

@DebugKaliumApi("Feeds large synthetic data into local DB for performance testing only.")
class ReactionsFeeder(
    private val localDatabase: UserDatabaseBuilder,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * Adds massive amount of reactions for benchmarking performance.
     *
     * @param conversationId
     */
    suspend fun feed(conversationId: ConversationIDEntity) = withContext(dispatcher.io) {

        val sql = """
    WITH senders AS (
        SELECT DISTINCT sender_user_id AS sender_id
        FROM Message
        WHERE conversation_id = ?
          AND sender_user_id IS NOT NULL
        LIMIT 5
    ),
    base AS (
        SELECT
            id,
            conversation_id,
            creation_date,
            rowid AS r
        FROM Message
        WHERE conversation_id = ?
          AND sender_user_id IS NOT NULL
    ),
    emojis AS (
        SELECT '👍' AS emoji
        UNION ALL SELECT '❤️'
        UNION ALL SELECT '😂'
        UNION ALL SELECT '🔥'
        UNION ALL SELECT '🎉'
        UNION ALL SELECT '😮'
    )
    INSERT OR IGNORE INTO Reaction (message_id, conversation_id, sender_id, emoji, date)
    SELECT
        b.id,
        b.conversation_id,
        s.sender_id,
        e.emoji,
        STRFTIME('%Y-%m-%dT%H:%M:%fZ', b.creation_date / 1000, 'unixepoch') AS date
    FROM base AS b
    JOIN senders AS s
    JOIN emojis AS e
    WHERE (b.r % 2) = 0;
""".trimIndent()

        localDatabase.sqlDriver.execute(
            identifier = null,
            sql = sql,
            parameters = 2
        ) {
            bindString(0, conversationId.toString())
            bindString(1, conversationId.toString())
        }
    }
}
