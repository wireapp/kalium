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
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.migration.SchemaMigrationTest
import kotlinx.coroutines.test.runTest
import org.sqlite.SQLiteConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration136Test : SchemaMigrationTest() {

    companion object {
        const val LAST_KNOWN_SCHEMA = 124L
        const val MIGRATION_TO_TEST = 136L
    }

    @Test
    fun givenConversationsBeforeMigration_whenMigrating_thenUsesConversationTypeForGroupKinds() = runTest {
        val dbFile = File("build/user-schema-dumps/migration-$MIGRATION_TO_TEST-test.db")
        dbFile.parentFile.mkdirs()
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/$LAST_KNOWN_SCHEMA.db").copyTo(dbFile, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        try {
            // Migrate to the version just before the migration we want to test
            UserDatabase.Schema.awaitMigrate(driver, LAST_KNOWN_SCHEMA, MIGRATION_TO_TEST)

            // Insert test data into the database before the migration
            driver.execute(
                null,
                """
                    INSERT INTO Conversation (
                        qualified_id,
                        name,
                        type,
                        is_channel,
                        mls_group_state,
                        protocol,
                        muted_status,
                        muted_time,
                        creator_id,
                        last_modified_date,
                        access_list,
                        access_role_list,
                        last_read_date,
                        mls_last_keying_material_update_date,
                        mls_cipher_suite,
                        receipt_mode,
                        incomplete_metadata,
                        archived,
                        history_sharing_retention_seconds
                    )
                    VALUES
                        (
                            'regular@wire.com',
                            'Regular group',
                            'GROUP',
                            0,
                            'ESTABLISHED',
                            'PROTEUS',
                            'ALL_ALLOWED',
                            0,
                            'creator',
                            0,
                            '[]',
                            '[]',
                            0,
                            0,
                            'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
                            'DISABLED',
                            0,
                            0,
                            0
                        ),
                        (
                            'channel@wire.com',
                            'Channel',
                            'GROUP',
                            1,
                            'ESTABLISHED',
                            'PROTEUS',
                            'ALL_ALLOWED',
                            0,
                            'creator',
                            0,
                            '[]',
                            '[]',
                            0,
                            0,
                            'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
                            'DISABLED',
                            0,
                            0,
                            0
                        ),
                        (
                            'one-on-one@wire.com',
                            'One on one',
                            'ONE_ON_ONE',
                            1,
                            'ESTABLISHED',
                            'PROTEUS',
                            'ALL_ALLOWED',
                            0,
                            'creator',
                            0,
                            '[]',
                            '[]',
                            0,
                            0,
                            'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
                            'DISABLED',
                            0,
                            0,
                            0
                        ),
                        (
                            'self@wire.com',
                            'Self',
                            'SELF',
                            1,
                            'ESTABLISHED',
                            'PROTEUS',
                            'ALL_ALLOWED',
                            0,
                            'creator',
                            0,
                            '[]',
                            '[]',
                            0,
                            0,
                            'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
                            'DISABLED',
                            0,
                            0,
                            0
                        ),
                        (
                            'connection-pending@wire.com',
                            'Connection pending',
                            'CONNECTION_PENDING',
                            1,
                            'ESTABLISHED',
                            'PROTEUS',
                            'ALL_ALLOWED',
                            0,
                            'creator',
                            0,
                            '[]',
                            '[]',
                            0,
                            0,
                            'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519',
                            'DISABLED',
                            0,
                            0,
                            0
                        )
                """.trimIndent(),
                0
            )

            // Migrate to the version that includes the migration we want to test
            UserDatabase.Schema.awaitMigrate(driver, MIGRATION_TO_TEST, MIGRATION_TO_TEST + 1)

            assertEquals(
                mapOf(
                    "channel@wire.com" to "CHANNEL",
                    "connection-pending@wire.com" to "CONNECTION_PENDING",
                    "one-on-one@wire.com" to "ONE_ON_ONE",
                    "regular@wire.com" to "GROUP",
                    "self@wire.com" to "SELF"
                ),
                driver.selectConversationTypes()
            )
            assertEquals(false, driver.conversationTableHasColumn("is_channel"))
            assertEquals(false, driver.conversationTableHasColumn("group_type"))
        } finally {
            driver.close()
            dbFile.delete()
        }
    }

    @Test
    fun givenForeignKeysEnabledAndConversationHistory_whenMigratingInTransaction_thenKeepsDependentData() = runTest {
        val dbFile = File("build/user-schema-dumps/migration-$MIGRATION_TO_TEST-foreign-keys-test.db")
        dbFile.parentFile.mkdirs()
        File("src/commonTest/kotlin/com/wire/kalium/persistence/schemas/$LAST_KNOWN_SCHEMA.db").copyTo(dbFile, overwrite = true)
        val sqliteConfig = SQLiteConfig().apply { enforceForeignKeys(true) }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", sqliteConfig.toProperties())

        try {
            UserDatabase.Schema.awaitMigrate(driver, LAST_KNOWN_SCHEMA, MIGRATION_TO_TEST)

            driver.execute(null, "INSERT INTO User(qualified_id) VALUES ('user@wire.com')", 0)
            driver.execute(
                null,
                """
                    INSERT INTO Conversation (
                        qualified_id, type, is_channel, mls_group_state, protocol, creator_id,
                        last_modified_date, access_list, access_role_list, mls_cipher_suite
                    ) VALUES (
                        'conversation@wire.com', 'GROUP', 0, 'ESTABLISHED', 'PROTEUS', 'user@wire.com',
                        0, '[]', '[]', 'MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519'
                    )
                """.trimIndent(),
                0
            )
            driver.execute(
                null,
                """
                    INSERT INTO Message(
                        id, content_type, conversation_id, creation_date, sender_user_id, status, visibility
                    ) VALUES (
                        'message-id', 'TEXT', 'conversation@wire.com', 0, 'user@wire.com', 'SENT', 'VISIBLE'
                    )
                """.trimIndent(),
                0
            )
            driver.execute(
                null,
                "INSERT INTO Member(user, conversation, role) VALUES ('user@wire.com', 'conversation@wire.com', 'wire_member')",
                0
            )
            driver.execute(
                null,
                "INSERT INTO UnreadEvent(id, type, conversation_id, creation_date) VALUES ('message-id', 'MESSAGE', 'conversation@wire.com', 0)",
                0
            )
            driver.execute(
                null,
                "INSERT INTO Reaction(message_id, conversation_id, sender_id, emoji, date) " +
                    "VALUES ('message-id', 'conversation@wire.com', 'user@wire.com', '👍', '0')",
                0
            )
            object : SuspendingTransacterImpl(driver) {}.transaction {
                UserDatabase.Schema.awaitMigrate(driver, MIGRATION_TO_TEST, MIGRATION_TO_TEST + 1)
            }

            assertEquals(
                mapOf(
                    "Conversation" to 1L,
                    "Message" to 1L,
                    "Member" to 1L,
                    "UnreadEvent" to 1L,
                    "Reaction" to 1L,
                ),
                listOf("Conversation", "Message", "Member", "UnreadEvent", "Reaction")
                    .associateWith { driver.rowCount(it) }
            )
        } finally {
            driver.close()
            dbFile.delete()
        }
    }

    private fun JdbcSqliteDriver.selectConversationTypes(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        executeQuery(
            null,
            "SELECT qualified_id, type FROM Conversation ORDER BY qualified_id",
            mapper = { cursor ->
                while (cursor.next().value) {
                    result[cursor.getString(0)!!] = cursor.getString(1)!!
                }
                QueryResult.Unit
            },
            0
        )
        return result
    }

    private fun JdbcSqliteDriver.conversationTableHasColumn(columnName: String): Boolean {
        var hasColumn = false
        executeQuery(
            null,
            "PRAGMA table_info(Conversation)",
            mapper = { cursor ->
                while (cursor.next().value) {
                    hasColumn = hasColumn || cursor.getString(1) == columnName
                }
                QueryResult.Unit
            },
            0
        )
        return hasColumn
    }

    private fun JdbcSqliteDriver.rowCount(table: String): Long {
        var result = 0L
        executeQuery(
            null,
            "SELECT COUNT(*) FROM $table",
            mapper = { cursor ->
                if (cursor.next().value) {
                    result = cursor.getLong(0)!!
                }
                QueryResult.Unit
            },
            0
        )
        return result
    }
}
