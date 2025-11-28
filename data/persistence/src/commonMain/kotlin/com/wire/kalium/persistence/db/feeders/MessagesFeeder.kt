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
 * Debug-only feeder that generates synthetic **text messages** in a given conversation.
 *
 * What it does:
 *
 * 1. Picks up to **5 members** of that conversation that:
 *    - exist in the `Member` table for this conversation,
 *    - have at least one **valid** client in the `Client` table (`is_valid = 1`).
 * 2. For those users it generates up to **10k text messages** in total
 *    (max 5 users × 2000 messages per user).
 *
 * Generated messages:
 *
 * - `content_type = 'TEXT'`
 * - `visibility = 'VISIBLE'`
 * - `status = 'SENT'`
 * - `sender_user_id` = one of the 5 selected conversation members
 * - `sender_client_id` = a real, existing `Client.id` for that user (never null)
 * - `text_body` = simple synthetic text
 *
 * ⚠️ This API must **never** be used in production. It is purely for performance / UX testing.
 */
@DebugKaliumApi("Populates synthetic text messages for performance testing. Debug-only.")
class MessagesFeeder(
    private val localDatabase: UserDatabaseBuilder,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) {

    /**
     * Populates the given conversation with synthetic text messages.
     *
     * @param conversationId Conversation identifier (`QualifiedIDEntity`).
     */
    @Suppress("LongMethod")
    suspend fun feed(conversationId: ConversationIDEntity) = withContext(dispatcher.io) {
        // 1. Insert synthetic Message rows.
        //
        // We:
        //  - find up to 5 conversation members that have at least one valid client (conv_users),
        //  - generate a cartesian product of small number tables (numsA..numsD) to get
        //    up to 2000 messages per user (10 × 10 × 10 × 2 = 2000),
        //  - use ROW_NUMBER() as a stable sequence number (rn),
        //  - build message_id = "dbg_msg_<rn>".
        //
        // Result: up to 5 users × 2000 messages = 10 000 messages in this conversation.
        val insertMessagesSql = """
            WITH conv AS (
                SELECT qualified_id
                FROM Conversation
                WHERE qualified_id = ?
                LIMIT 1
            ),
            conv_users AS (
                -- Up to 5 members of this conversation that have at least one valid client.
                SELECT
                    m.user                                      AS user_id,
                    COALESCE(u.name, '')                        AS display_name,
                    (
                        SELECT c.id
                        FROM Client c
                        WHERE c.user_id = m.user
                          AND c.is_valid = 1
                        ORDER BY c.id
                        LIMIT 1
                    )                                           AS client_id
                FROM Member m
                JOIN User u ON u.qualified_id = m.user
                WHERE m.conversation = (SELECT qualified_id FROM conv)
                  AND EXISTS (
                      SELECT 1
                      FROM Client c2
                      WHERE c2.user_id = m.user
                        AND c2.is_valid = 1
                  )
                GROUP BY m.user
                ORDER BY m.user
                LIMIT 5
            ),
            numsA AS (
                SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            ),
            numsB AS (
                SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            ),
            numsC AS (
                SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            ),
            numsD AS (
                SELECT 0 AS n UNION ALL SELECT 1
            ),
            expanded AS (
                SELECT
                    cu.user_id,
                    cu.client_id,
                    cu.display_name,
                    ROW_NUMBER() OVER (
                        ORDER BY cu.user_id, a.n, b.n, c.n, d.n
                    ) AS rn,
                    -- Spread messages backwards in 1-minute steps from "now"
                    (CAST(strftime('%s','now') AS INTEGER) * 1000)
                        - ((a.n * 1000 + b.n * 100 + c.n * 10 + d.n) * 60000) AS creation_ms
                FROM conv_users cu
                CROSS JOIN numsA a
                CROSS JOIN numsB b
                CROSS JOIN numsC c
                CROSS JOIN numsD d
            )
            INSERT OR IGNORE INTO Message(
                id,
                content_type,
                conversation_id,
                creation_date,
                sender_user_id,
                sender_client_id,
                status,
                last_edit_date,
                visibility,
                expects_read_confirmation,
                expire_after_millis,
                self_deletion_end_date
            )
            SELECT
                'dbg_msg_' || rn                             AS id,
                'TEXT'                                       AS content_type,
                (SELECT qualified_id FROM conv)              AS conversation_id,
                creation_ms                                  AS creation_date,
                user_id                                      AS sender_user_id,
                client_id                                    AS sender_client_id,
                'SENT'                                       AS status,
                NULL                                         AS last_edit_date,
                'VISIBLE'                                    AS visibility,
                0                                            AS expects_read_confirmation,
                NULL                                         AS expire_after_millis,
                NULL                                         AS self_deletion_end_date
            FROM expanded;
        """.trimIndent()

        localDatabase.sqlDriver.execute(
            identifier = null,
            sql = insertMessagesSql,
            parameters = 1
        ) {
            bindString(0, conversationId.toString())
        }

        // 2. Insert matching MessageTextContent rows for the same "dbg_msg_<rn>" ids.
        //
        //  - We reuse the same conv / conv_users / numsA..numsD / expanded pipeline,
        //    so that rn matches the one used above for Message.
        val insertTextSql = """
            WITH conv AS (
                SELECT qualified_id
                FROM Conversation
                WHERE qualified_id = ?
                LIMIT 1
            ),
            conv_users AS (
                SELECT
                    m.user                                      AS user_id,
                    COALESCE(u.name, '')                        AS display_name
                FROM Member m
                JOIN User u ON u.qualified_id = m.user
                WHERE m.conversation = (SELECT qualified_id FROM conv)
                  AND EXISTS (
                      SELECT 1
                      FROM Client c2
                      WHERE c2.user_id = m.user
                        AND c2.is_valid = 1
                  )
                GROUP BY m.user
                ORDER BY m.user
                LIMIT 5
            ),
            numsA AS (
                SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            ),
            numsB AS (
                SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            ),
            numsC AS (
                SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
            ),
            numsD AS (
                SELECT 0 AS n UNION ALL SELECT 1
            ),
            expanded AS (
                SELECT
                    cu.user_id,
                    cu.display_name,
                    ROW_NUMBER() OVER (
                        ORDER BY cu.user_id, a.n, b.n, c.n, d.n
                    ) AS rn
                FROM conv_users cu
                CROSS JOIN numsA a
                CROSS JOIN numsB b
                CROSS JOIN numsC c
                CROSS JOIN numsD d
            )
            INSERT OR IGNORE INTO MessageTextContent(
                message_id,
                conversation_id,
                text_body,
                quoted_message_id,
                is_quote_verified,
                is_quoting_self
            )
            SELECT
                'dbg_msg_' || rn                              AS message_id,
                (SELECT qualified_id FROM conv)               AS conversation_id,
                'Synthetic debug message #' || rn             AS text_body,
                NULL                                          AS quoted_message_id,
                NULL                                          AS is_quote_verified,
                0                                             AS is_quoting_self
            FROM expanded;
        """.trimIndent()

        localDatabase.sqlDriver.execute(
            identifier = null,
            sql = insertTextSql,
            parameters = 1
        ) {
            bindString(0, conversationId.toString())
        }
    }
}
