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

class Migration165Test {
    @Test
    fun storedRequestsOfAcceptedUsersBecomeAcceptedAndOthersKeepTheirState() = runTest {
        val file = File.createTempFile("accepted-connection-requests-migration-", ".db")
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        try {
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 124, 165)
            }
            // Accepted incoming, accepted outgoing, still pending, blocked after the request, and no user row.
            listOf(
                "incoming" to "ACCEPTED",
                "outgoing" to "ACCEPTED",
                "pending" to "PENDING",
                "blocked" to "BLOCKED",
            ).forEach { (user, status) ->
                driver.execute(null, "INSERT INTO User(qualified_id, connection_status) VALUES ('$user@domain', '$status')", 0)
            }
            listOf(
                "incoming" to "PENDING",
                "outgoing" to "SENT",
                "pending" to "PENDING",
                "blocked" to "BLOCKED",
                "unknown" to "PENDING",
            ).forEach { (user, status) ->
                driver.execute(
                    null,
                    """
                    INSERT INTO Connection(from_id, conversation_id, qualified_conversation, to_id, last_update_date, qualified_to, status)
                    VALUES ('self', '$user', '$user@domain', '$user', 0, '$user@domain', '$status')
                    """.trimIndent(),
                    0
                )
            }

            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 165, 166)
            }

            assertEquals(
                listOf(
                    "blocked@domain BLOCKED",
                    "incoming@domain ACCEPTED",
                    "outgoing@domain ACCEPTED",
                    "pending@domain PENDING",
                    "unknown@domain PENDING",
                ),
                driver.strings("SELECT qualified_to || ' ' || status FROM Connection ORDER BY qualified_to")
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
