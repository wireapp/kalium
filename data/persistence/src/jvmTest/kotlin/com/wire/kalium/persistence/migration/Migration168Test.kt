/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration168Test {
    @Test
    fun onlyGroupsStuckWaitingForAWelcomeAreQueuedForJoin() = runTest {
        val file = File.createTempFile("pending-welcome-migration-", ".db")
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        try {
            object : SuspendingTransacterImpl(driver) {}.transaction { UserDatabase.Schema.awaitMigrate(driver, 124, 168) }
            fun conversation(id: String, type: String, protocol: String, state: String, epoch: Int, deleted: Int = 0) =
                driver.execute(
                    null,
                    """
                    INSERT INTO Conversation(qualified_id, type, mls_group_state, mls_epoch, protocol, creator_id, last_modified_date,
                        access_list, access_role_list, mls_cipher_suite, deleted_locally)
                    VALUES ('$id@domain', '$type', '$state', $epoch, '$protocol', 'self@domain', 0, '[]', '[]', 'UNKNOWN', $deleted)
                    """.trimIndent(),
                    0
                )
            conversation("mls", "GROUP", "MLS", "PENDING_WELCOME_MESSAGE", epoch = 3)
            conversation("mixed", "GROUP", "MIXED", "PENDING_WELCOME_MESSAGE", epoch = 3)
            conversation("new", "GROUP", "MLS", "PENDING_WELCOME_MESSAGE", epoch = 0)
            conversation("joined", "GROUP", "MLS", "ESTABLISHED", epoch = 3)
            conversation("deleted", "GROUP", "MLS", "PENDING_WELCOME_MESSAGE", epoch = 3, deleted = 1)
            conversation("direct", "ONE_ON_ONE", "MLS", "PENDING_WELCOME_MESSAGE", epoch = 3)

            object : SuspendingTransacterImpl(driver) {}.transaction { UserDatabase.Schema.awaitMigrate(driver, 168, 169) }

            assertEquals(
                listOf("join_mls_group_conversation mixed@domain", "join_mls_group_conversation mls@domain"),
                driver.strings("SELECT action_type || ' ' || qualified_id FROM PendingActions ORDER BY qualified_id")
            )
        } finally {
            driver.close()
            file.delete()
        }
    }

    private fun JdbcSqliteDriver.strings(sql: String): List<String> {
        val values = mutableListOf<String>()
        executeQuery(null, sql, { cursor ->
            while (cursor.next().value) values += cursor.getString(0)!!
            QueryResult.Unit
        }, 0)
        return values
    }
}
