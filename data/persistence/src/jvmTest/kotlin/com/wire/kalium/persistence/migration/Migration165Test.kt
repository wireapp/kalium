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
    fun onlyRequestsWhoseConnectionWasAcceptedBecomeAccepted() = runTest {
        val file = File.createTempFile("accepted-connection-requests-migration-", ".db")
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/124.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        try {
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 124, 165)
            }
            // user status, stored request, conversation type (null: none), expected request after the migration
            val cases = listOf(
                Case("incoming", "ACCEPTED", "PENDING", "ONE_ON_ONE", "ACCEPTED"),
                Case("outgoing", "ACCEPTED", "SENT", "ONE_ON_ONE", "ACCEPTED"),
                Case("ignored", "ACCEPTED", "IGNORED", "ONE_ON_ONE", "ACCEPTED"),
                Case("pending", "PENDING", "PENDING", "ONE_ON_ONE", "PENDING"),
                Case("teammate", "ACCEPTED", "PENDING", "CONNECTION_PENDING", "PENDING"),
                Case("blocked", "ACCEPTED", "BLOCKED", "ONE_ON_ONE", "BLOCKED"),
                Case("unknown", null, "PENDING", null, "PENDING"),
            )
            cases.forEach { case ->
                val id = "${case.user}@domain"
                case.userStatus?.let { driver.execute(null, "INSERT INTO User(qualified_id, connection_status) VALUES ('$id', '$it')", 0) }
                case.conversationType?.let {
                    driver.execute(
                        null,
                        """
                        INSERT INTO Conversation(qualified_id, type, mls_group_state, protocol, creator_id, last_modified_date,
                            access_list, access_role_list, mls_cipher_suite)
                        VALUES ('$id', '$it', 'ESTABLISHED', 'PROTEUS', 'self@domain', 0, '[]', '[]', 'UNKNOWN')
                        """.trimIndent(),
                        0
                    )
                }
                driver.execute(
                    null,
                    """
                    INSERT INTO Connection(from_id, conversation_id, qualified_conversation, to_id, last_update_date, qualified_to, status)
                    VALUES ('self', '${case.user}', '$id', '${case.user}', 0, '$id', '${case.request}')
                    """.trimIndent(),
                    0
                )
            }

            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, 165, 166)
            }

            assertEquals(
                cases.map { "${it.user}@domain ${it.expected}" }.sorted(),
                driver.strings("SELECT qualified_to || ' ' || status FROM Connection ORDER BY qualified_to")
            )
        } finally {
            driver.close()
            file.delete()
        }
    }

    private data class Case(
        val user: String,
        val userStatus: String?,
        val request: String,
        val conversationType: String?,
        val expected: String,
    )

    private fun JdbcSqliteDriver.strings(sql: String): List<String> {
        val values = mutableListOf<String>()
        executeQuery(null, sql, { cursor ->
            while (cursor.next().value) values += cursor.getString(0)!!
            QueryResult.Unit
        }, 0)
        return values
    }
}
