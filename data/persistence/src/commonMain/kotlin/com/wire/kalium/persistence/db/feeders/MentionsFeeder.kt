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
 * Debug-only feeder that injects synthetic @mentions into text messages for a given conversation.
 *
 * It does two things:
 *  1. Rewrites some text messages so their body contains @DisplayName tokens.
 *  2. Inserts matching rows into MessageMention with correct [start, length, user_id].
 *
 * Heuristics:
 *  - works only on TEXT messages with VISIBLE visibility
 *  - picks roughly every 5th text message in the conversation
 *  - attaches up to 3 mentions per message
 *  - uses display names of users who actually sent messages in this conversation
 *
 * ⚠️ Irreversible:
 *  - message text bodies are overwritten for selected messages
 *  - there is no automatic way back to the original content
 *  - must never be used in production
 */
@DebugKaliumApi("Populates synthetic @mentions for performance testing. Debug-only and irreversible.")
class MentionsFeeder(
    private val localDatabase: UserDatabaseBuilder,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * Feeds mentions into the given conversation:
     *  - updates MessageTextContent.text_body with @DisplayName tokens
     *  - fills MessageMention with matching ranges for those tokens
     */
    @Suppress("LongMethod")
    suspend fun feed(conversationId: ConversationIDEntity) = withContext(dispatcher.io) {
        // 1) Overwrite message texts to include @DisplayName
        val updateTextBodiesSql = """
            WITH base AS (
                SELECT
                    m.id              AS message_id,
                    m.conversation_id AS conversation_id,
                    m.rowid           AS r
                FROM Message m
                WHERE m.conversation_id = ?
                  AND m.content_type = 'TEXT'
                  AND m.visibility IN ('VISIBLE')
                  AND m.sender_user_id IS NOT NULL
            ),
            target_msgs AS (
                SELECT *
                FROM base
                WHERE (r % 5) = 0
            ),
            conv_users AS (
                SELECT DISTINCT
                    m.sender_user_id AS user_id
                FROM Message m
                WHERE m.conversation_id = ?
                  AND m.sender_user_id IS NOT NULL
            ),
            conv_users_named AS (
                SELECT
                    u.qualified_id AS user_id,
                    u.name         AS name
                FROM conv_users cu
                JOIN User u ON u.qualified_id = cu.user_id
                WHERE u.name IS NOT NULL AND u.name <> ''
            ),
            pairs AS (
                SELECT
                    tm.message_id,
                    tm.conversation_id,
                    '@' || cun.name AS token,
                    ROW_NUMBER() OVER (
                        PARTITION BY tm.message_id
                        ORDER BY cun.user_id
                    ) AS idx
                FROM target_msgs tm
                JOIN conv_users_named cun
            ),
            msg_bodies AS (
                SELECT
                    message_id,
                    conversation_id,
                    TRIM(GROUP_CONCAT(token, ' ')) AS new_body
                FROM pairs
                WHERE idx <= 3
                GROUP BY message_id, conversation_id
            )
            UPDATE MessageTextContent
            SET text_body = (
                SELECT new_body
                FROM msg_bodies mb
                WHERE mb.message_id = MessageTextContent.message_id
                  AND mb.conversation_id = MessageTextContent.conversation_id
            )
            WHERE EXISTS (
                SELECT 1
                FROM msg_bodies mb
                WHERE mb.message_id = MessageTextContent.message_id
                  AND mb.conversation_id = MessageTextContent.conversation_id
            );
        """.trimIndent()

        localDatabase.sqlDriver.execute(
            identifier = null,
            sql = updateTextBodiesSql,
            parameters = 2
        ) {
            val id = conversationId.toString()
            bindString(0, id) // base.conversation_id
            bindString(1, id) // conv_users.conversation_id
        }

        // 2) Calculate the position of @DisplayName in text_body and insert it into MessageMention
        val insertMentionsSql = """
            WITH msgs AS (
                SELECT
                    m.id              AS message_id,
                    m.conversation_id AS conversation_id,
                    m.rowid           AS r
                FROM Message m
                WHERE m.conversation_id = ?
                  AND m.content_type = 'TEXT'
                  AND m.visibility IN ('VISIBLE')
                  AND m.sender_user_id IS NOT NULL
            ),
            selected_msgs AS (
                SELECT *
                FROM msgs
                WHERE (r % 5) = 0
            ),
            conv_users AS (
                SELECT DISTINCT
                    m.sender_user_id AS user_id
                FROM Message m
                WHERE m.conversation_id = ?
                  AND m.sender_user_id IS NOT NULL
            ),
            conv_users_named AS (
                SELECT
                    u.qualified_id AS user_id,
                    u.name         AS name
                FROM conv_users cu
                JOIN User u ON u.qualified_id = cu.user_id
                WHERE u.name IS NOT NULL AND u.name <> ''
            ),
            tokens AS (
                SELECT
                    sm.message_id,
                    sm.conversation_id,
                    cun.user_id,
                    '@' || cun.name AS token,
                    ROW_NUMBER() OVER (
                        PARTITION BY sm.message_id
                        ORDER BY cun.user_id
                    ) AS idx
                FROM selected_msgs sm
                JOIN conv_users_named cun
            ),
            msg_bodies AS (
                SELECT
                    mt.message_id,
                    mt.conversation_id,
                    mt.text_body
                FROM MessageTextContent mt
                JOIN selected_msgs sm
                  ON sm.message_id      = mt.message_id
                 AND sm.conversation_id = mt.conversation_id
            ),
            positions AS (
                SELECT
                    t.message_id,
                    t.conversation_id,
                    t.user_id,
                    instr(mb.text_body, t.token) - 1 AS start_pos, -- 0-based
                    length(t.token)                  AS length
                FROM tokens t
                JOIN msg_bodies mb
                  ON mb.message_id      = t.message_id
                 AND mb.conversation_id = t.conversation_id
                WHERE t.idx <= 3
                  AND instr(mb.text_body, t.token) > 0
            )
            INSERT OR IGNORE INTO MessageMention (message_id, conversation_id, start, length, user_id)
            SELECT
                message_id,
                conversation_id,
                start_pos,
                length,
                user_id
            FROM positions;
        """.trimIndent()

        localDatabase.sqlDriver.execute(
            identifier = null,
            sql = insertMentionsSql,
            parameters = 2
        ) {
            val id = conversationId.toString()
            bindString(0, id) // msgs.conversation_id
            bindString(1, id) // conv_users.conversation_id
        }
    }
}
